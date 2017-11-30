/**
 * Copyright (C) 2017 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 *
 * \file        Wbs.cpp
 *
 * \brief       WBS Live Stream Decoder
 *
 * \author      Rajesh Hingorani
 *
 * \date        11/29/2017
 *
 * \note        Modifications to code provided by Light Blue Optics Ltd.
 *
 * This module does the actual decoding of the WBS Live Stream
 *
 */

///////////////////////////////////////////////////////////////////////////////

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/select.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <fcntl.h>
#include <unistd.h>
#include <netinet/in.h>
#include <netdb.h>
#include <sys/time.h>
#include <ctype.h>
#include <assert.h>

#include <openssl/ssl.h>
#include <openssl/err.h>

#include "csioCommonShare.h"
#include "TileDecoder.h"
#include "Wbs.h"

#define USE_SURFACE 1

jobject gWbsApp; // keep global reference for calling functions

Wbs_t gWbs[MAX_STREAMS];

static char *getHexMessage(char *msg, int msglen)
{
	static char hexBuffer[4096];
	char *p = hexBuffer;
	char *end = p + sizeof(hexBuffer) - 4;

	for (int i=0; i < msglen && p < end; i++) {
		sprintf(p, "%02x ", msg[i]);
		p+=3;
	}
	*p = '\0';
	return hexBuffer;
}

Wbs_t *wbs_get_stream_data(int sessId)
{
	return &gWbs[sessId];
}

jobject wbs_get_app()
{
	return gWbsApp;
}

void wbs_set_app(jobject app)
{
	gWbsApp = app;
}

static void wbs_set_buffer_size(Wbs_t *pWbs, int w, int h)
{
	ANativeWindow_Buffer windowBuffer;
	pWbs->width = w;
	pWbs->height = h;
	if (pWbs->native_window) {
		ANativeWindow_setBuffersGeometry(pWbs->native_window, pWbs->width, pWbs->height, pWbs->format);
		// Needs to lock the window buffer to get its properties.
		if (ANativeWindow_lock(pWbs->native_window, &windowBuffer, NULL) >= 0) {
			int bw = windowBuffer.width;
			int bh = windowBuffer.height;
			int stride = windowBuffer.stride;
			ANativeWindow_unlockAndPost(pWbs->native_window);
	        CSIO_LOG(eLogLevel_debug, "%s: buffers resized to %dx%d with stride=%d", __FUNCTION__, bw, bh, stride);
		} else {
	        CSIO_LOG(eLogLevel_error, "%s: unable to set buffers to size of %dx%d", __FUNCTION__, w, h);
		}
    } else {
        CSIO_LOG(eLogLevel_error, "%s: cannot set buffer sizes without native window", __FUNCTION__);
    }
	wbs_update_window(pWbs->streamId, w, h);
}

static void wbs_render(Wbs_t *pWbs, unsigned char *frameBuffer, int w, int h)
{
	ANativeWindow *native_window = (pWbs) ? pWbs->native_window : NULL;
	ANativeWindow_Buffer windowBuffer;

	CSIO_LOG((pWbs->frameCount < 10) ? eLogLevel_debug : eLogLevel_extraVerbose, "%s: render image %d of size %dx%d", __FUNCTION__, pWbs->frameCount, w, h);
	pWbs->frameCount++;

	if (native_window == NULL) {
		CSIO_LOG(eLogLevel_error, "%s: No window attached to stream", __FUNCTION__);
		return;
	}

    // Locks the window buffer for drawing.
    if (ANativeWindow_lock(native_window, &windowBuffer, NULL) < 0) {
        CSIO_LOG(eLogLevel_error, "unable to get lock on window buffer");
        return;
    }
    if (w > windowBuffer.width) w = windowBuffer.width;
    if (h > windowBuffer.height) h = windowBuffer.height;
    if (windowBuffer.stride == w) {
    	memcpy(windowBuffer.bits, frameBuffer, w*h*sizeof(uint32_t));
    } else {
    	for (int row=0; row < h; row++) {
    		uint32_t *winbuf_line = ((uint32_t*) (windowBuffer.bits)) + row*windowBuffer.stride;
    		uint32_t *frmbuf_line = ((uint32_t*) (frameBuffer)) + row*w;
    		memcpy(winbuf_line, frmbuf_line, w*sizeof(uint32_t));
    	}
    }
    // Unlocks the window buffer after drawing.
    ANativeWindow_unlockAndPost(native_window);
}

static void wbs_clear_window(Wbs_t *pWbs)
{
	ANativeWindow *native_window = (pWbs) ? pWbs->native_window : NULL;
	ANativeWindow_Buffer windowBuffer;
	CSIO_LOG(eLogLevel_debug, "%s: clear window", __FUNCTION__);
	if (native_window == NULL) {
		CSIO_LOG(eLogLevel_error, "%s: No window attached to stream", __FUNCTION__);
		return;
	}

    // Locks the window buffer for drawing.
    if (ANativeWindow_lock(native_window, &windowBuffer, NULL) < 0) {
        CSIO_LOG(eLogLevel_error, "unable to get lock on window buffer");
        return;
    }
    // Clears the window.
    memset(windowBuffer.bits, 0, windowBuffer.stride * windowBuffer.height * sizeof(uint32_t*));

    // Unlocks the window buffer after drawing.
    ANativeWindow_unlockAndPost(native_window);
}

static int open_socket(char const * hostname, int portnumber)
{
  int fd = -1;
  fd = socket(AF_INET, SOCK_STREAM, 0);
  struct sockaddr_in inaddr;
  uint16_t port = portnumber;
  struct hostent * server =  gethostbyname(hostname);
  memset(&inaddr, 0, sizeof(inaddr));
  inaddr.sin_family = AF_INET;
  inaddr.sin_addr.s_addr = *(uint32_t *)(server->h_addr);
  inaddr.sin_port = htons(port);
  if (connect(fd, (struct sockaddr *)&inaddr, sizeof(inaddr)) < 0) {
	  CSIO_LOG(eLogLevel_debug, "%s: Failed to connect websock host=%s port=%d", __FUNCTION__, hostname, portnumber);
	  return -1;
  }
  return fd;
}

static void do_live_view(Wbs_t *pWbs, int fd, SSL * pSSL, char const * origin = "https://kaptivo.live")
{
	char str[65536];
	char * ptr = str;
	pWbs->width = 0;
	pWbs->height = 0;

	CSIO_LOG(eLogLevel_debug, "%s: sending http request: resource=%s host=%s", __FUNCTION__, pWbs->resource, pWbs->hostname);
	// Send the request header
	ptr += sprintf(ptr, "GET %s HTTP/1.1\r\n", pWbs->resource);
	ptr += sprintf(ptr, "Host: %s\r\n", pWbs->hostname);
	ptr += sprintf(ptr, "Upgrade: websockets\r\n");
	ptr += sprintf(ptr, "Connection: Upgrade\r\n");
	ptr += sprintf(ptr, "Sec-Websocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n");
	ptr += sprintf(ptr, "Origin: %s\r\n", origin);
	ptr += sprintf(ptr, "Sec-Websocket-Version: 13\r\n");
	ptr += sprintf(ptr, "\r\n");
	char const * ptr0 = str;
	while (ptr0 < ptr) {
		int n = pSSL ? SSL_write(pSSL, ptr0, ptr-ptr0) : write(fd, ptr0, ptr-ptr0);
		if (n < 1) {
			CSIO_LOG(eLogLevel_debug, "%s: Error sending http message %d", errno);
			return;
		}
		ptr0 += n;
	}

	// Consume the response header (which we assume is less than 64K bytes)
	ptr = str;
	while (ptr < str+65535 && (ptr < str+4 || ptr[-1]!='\n' || (ptr[-3]!='\n' && ptr[-2]!='\n'))) {
		if ((pSSL ? SSL_read(pSSL, ptr, 1) : read(fd, ptr, 1)) > 0)
			ptr++;
		else {
			CSIO_LOG(eLogLevel_debug, "%s: Error reading response message %d", errno);
			return;
		}
	}
	*ptr='\0';

	// OpenCV uses BGR byte order; but for raw dumping we prefer RGB.
#ifdef USE_SURFACE
	static const TileDecoder::FBType fbType = TileDecoder::FBTYPE_RGBA;
#else
	static const TileDecoder::FBType fbType = TileDecoder::FBTYPE_RGB;
#endif

	CSIO_LOG(eLogLevel_debug, "%s: creating live stream decoder", __FUNCTION__);
	// Now we can start using the LiveView protocol (with WebSocket framing)
	TileDecoder deco(true, true, fbType);

	// Main loop: receive and handle bytes from socket and periodically send keep-alives
	int keepalive_counter = 0;
	while (!pWbs->requestStop) {
		fd_set fds;
		FD_ZERO(&fds);
		FD_SET(fd, &fds);
		struct timeval tv;
		tv.tv_sec  = 0;
		tv.tv_usec = 200000;
		if ((pSSL && SSL_pending(pSSL) > 0) || select(fd+1, &fds, 0, 0, &tv) > 0) {
			int n = pSSL ? SSL_read(pSSL, str, 65536) : read(fd, str, 65536);
			if (n <= 0) break;
			int pos = 0;
			while (pos < n) {
				int f;
				int nr = n-pos;
				//CSIO_LOG(eLogLevel_debug, "%s: msg start (%d): %s", __FUNCTION__, nr, getHexMessage((char *)(str+pos), (nr > 16)?16:nr));
				int n1 = deco.handleData(reinterpret_cast<unsigned char *>(str+pos), nr, &f);
				//CSIO_LOG(eLogLevel_debug, "%s: consumed %d of %d bytes f=%x", __FUNCTION__, n1, n, f);
				pos += n1;
				if (deco.dimensionsAvailable() && (pWbs->width != deco.getWidth() || pWbs->height != deco.getHeight()))
				{
					CSIO_LOG(eLogLevel_debug, "%s: got new dimensions (w=%d h=%d)", __FUNCTION__, deco.getWidth(), deco.getHeight());
					wbs_set_buffer_size(pWbs, deco.getWidth(), deco.getHeight());
			    	wbs_SendVideoPlayingStatusMessage(pWbs->streamId, STREAMSTATE_STARTED);
				}
				if (f & TileDecoder::FLAG_TXDATA) {
					int n2send = deco.numBytesToSend();
					if (n2send) {
						if ((pSSL ? SSL_write(pSSL, deco.getDataToSend(), n2send) : write(fd, deco.getDataToSend(), n2send)) < 1) {
							pos = -1;
							break;
						}
					}
				}
				if (f & TileDecoder::FLAG_FRAME) {
					CSIO_LOG(eLogLevel_extraVerbose, "%s: got frame (w=%d h=%d)", __FUNCTION__, deco.getWidth(), deco.getHeight());
					pWbs->backoffInSecs = 1;		// reduce backoff to min value since we have valid connection now
					if (!pWbs->isPaused) {			// if paused skip rendering of any new frames
#ifdef USE_SURFACE
						wbs_render(pWbs, deco.getBuffer(), deco.getWidth(), deco.getHeight());
#else
						static int frame_counter = 0;
						char str[64];
						sprintf(str, "/logs/ramdiskLogs/image%04d.ppm", frame_counter++);
						FILE * fpout = fopen(str, "wb");
						if (fpout) {
							fprintf(fpout, "P6 %d %d 255\n", deco.getWidth(), deco.getHeight());
							fwrite(deco.getBuffer(), deco.getWidth()*3, deco.getHeight(), fpout);
							fclose(fpout);
							CSIO_LOG(eLogLevel_debug, "%s: Wrote image file %s\n", __FUNCTION__, str);
						}
#endif
					}
					else
					{
						CSIO_LOG(eLogLevel_extraVerbose, "%s: not rendering image - in paused state", __FUNCTION__);
					}
				}
				if (f & (TileDecoder::FLAG_CLOSE | TileDecoder::FLAG_ERROR)) {
					if (f & TileDecoder::FLAG_ERROR)
					{
						CSIO_LOG(eLogLevel_error, "%s: got ERROR event ", __FUNCTION__);
					}
					if (f & TileDecoder::FLAG_CLOSE)
					{
						CSIO_LOG(eLogLevel_debug, "%s: got CLOSE event ", __FUNCTION__);
					}
					pos = -1;
					break;
				}
			}
			if (pos < 0) break;
		}
		else {
			if (!(++keepalive_counter & 7)) {
				deco.solicitKeepAlive();
				int n2send = deco.numBytesToSend();
				if (n2send) if ((pSSL ? SSL_write(pSSL, deco.getDataToSend(), n2send) : write(fd, deco.getDataToSend(), n2send)) < 1) break;
			}
		}
	}
}

bool wbs_verify_url(const char *url, bool *secure, char *hostname, int hostnameLen, int *portnumber, char *resource, int resourceLen)
{
	// Check that the argument is a full ws: or wss: URL
	const char * wsurl = url;
	*secure = (strncmp(wsurl, "wss://", 6) == 0);
	if (!*secure && strncmp(wsurl, "ws://", 5) != 0) {
		return false;
	}
	const char * ptr = wsurl + (secure ? 6 : 5);
	int len = 0;
	while (*ptr && *ptr != '/' && *ptr != ':' && len < hostnameLen) {
		hostname[len++] = *ptr++;
	}
	hostname[len] = '\0';
	*portnumber = *secure ? 443 : 80;
	if (*ptr==':') {
		sscanf(ptr+1, "%d", portnumber);
		while (*ptr && *ptr != '/') ++ptr;
	}
	if (*ptr != '/') {
		return false;
	}
	strncpy(resource, ptr, resourceLen); //TODO fix this
	return true;
}

int errcb(const char *str, size_t len, void *u)
{
	CSIO_LOG(eLogLevel_error, "%s", str);
	return 0;
}

static int wbs_start_connection(Wbs_t *pWbs)
{
	int rv=0;
	wbs_SendVideoPlayingStatusMessage(pWbs->streamId, STREAMSTATE_CONNECTING);

	CSIO_LOG(eLogLevel_debug, "%s: opening socket host=%s port=%d", __FUNCTION__, pWbs->hostname, pWbs->portnumber);
	int fd = open_socket(pWbs->hostname, pWbs->portnumber);
	if (fd < 0) {
		CSIO_LOG(eLogLevel_error, "%s: could not open socket", __FUNCTION__);
		return 1;
	}

	SSL_CTX * ctx = 0;
	SSL * pSSL = 0;

	if (pWbs->secure) {
		SSL_library_init();
		OpenSSL_add_all_algorithms();
		SSL_load_error_strings();
		ctx = SSL_CTX_new(TLSv1_2_client_method());
		pSSL = ctx ? SSL_new(ctx) : 0;
		if (pSSL == 0) {
			rv = 1;
			goto closesocket;
		}
		SSL_set_fd(pSSL, fd);
		if (SSL_connect(pSSL) < 0) {
			CSIO_LOG(eLogLevel_error, "%s: error establishing SSL connection");
			ERR_print_errors_cb(errcb, NULL);
			rv = 1;
			goto closesocket;
		}
	}

	CSIO_LOG(eLogLevel_debug, "%s: launching live view", __FUNCTION__);
	do_live_view(pWbs, fd, pSSL);

	CSIO_LOG(eLogLevel_debug, "%s: live view stream finished requestStop=%d", __FUNCTION__, pWbs->requestStop);
closesocket:
	if (pWbs->secure) {
		if (pSSL != 0)
			SSL_free(pSSL);
		if (ctx != 0)
			SSL_CTX_free(ctx);
	}
	if (fd >= 0)
		close(fd);
	CSIO_LOG(eLogLevel_debug, "%s: exiting with rv=%d", __FUNCTION__, rv);
	return rv;
}

static void wbs_stop_connection(Wbs_t *pWbs)
{
	if (pWbs->isStarted) {
		pWbs->requestStop = true;
		CSIO_LOG(eLogLevel_debug, "%s: requesting stop for streamId %d (requestStop=%d)", __FUNCTION__, pWbs->streamId, pWbs->requestStop);
	}
	else
	{
		CSIO_LOG(eLogLevel_debug, "%s: stream %d not started ", __FUNCTION__, pWbs->streamId);
	}
}

static void *wbsThread(void *arg)
{
	Wbs_t *pWbs = (Wbs_t *) arg;

	pWbs->isStarted = true;
	pWbs->requestStop = false;
	pWbs->backoffInSecs = RESTART_MAX_BACKOFF_SECS;
	pWbs->frameCount = 0;
	CSIO_LOG(eLogLevel_debug, "%s: requestStop=%s", __FUNCTION__, (pWbs->requestStop)?"true":"false");
	while (!wbs_start_connection(pWbs)) {
		if (!pWbs->requestStop)
		{
			CSIO_LOG(eLogLevel_error, "Failed to start WBS connection - retry in %d secs\n", pWbs->backoffInSecs);
			sleep(pWbs->backoffInSecs);
			pWbs->backoffInSecs *= 2;
			if (pWbs->backoffInSecs > RESTART_MAX_BACKOFF_SECS)
				pWbs->backoffInSecs = RESTART_MAX_BACKOFF_SECS;
		} else {
			break;
		}
	}

	if (!pWbs->requestStop)
	{
		CSIO_LOG(eLogLevel_error, "Exiting Wbs thread prematurely without explicit stop requested\n");
	}
    pWbs->isStarted = false;
	CSIO_LOG(eLogLevel_verbose, "Exiting Wbs thread...\n");
	return(NULL);
}


void wbs_setUrl(const char *url, int sessId)
{
	bool secure;
	char hostname[256];
	char resource[256];
	int portnumber;
	Wbs_t *pWbs = &gWbs[sessId];
	pWbs->streamId = sessId;

	strncpy(pWbs->url, url, sizeof(pWbs->url));
    CSIO_LOG(eLogLevel_debug, "%s: url='%s'", __FUNCTION__, pWbs->url);
}

static bool wbs_useUrl(Wbs_t *pWbs)
{
	bool secure;
	char hostname[256];
	char resource[256];
	int portnumber;

    CSIO_LOG(eLogLevel_debug, "%s: url='%s'", __FUNCTION__, pWbs->url);
	if (!wbs_verify_url(pWbs->url, &secure, hostname, sizeof(hostname), &portnumber, resource, sizeof(resource))) {
	    CSIO_LOG(eLogLevel_error, "%s: input url='%s' does not parse successfully", __FUNCTION__, pWbs->url);
	    pWbs->urlIsValid = false;
		return false;
	}

	strncpy(pWbs->hostname, hostname, sizeof(pWbs->hostname));
    CSIO_LOG(eLogLevel_debug, "%s: host=%s", __FUNCTION__, pWbs->hostname);
	strncpy(pWbs->resource, resource, sizeof(pWbs->resource));
    CSIO_LOG(eLogLevel_debug, "%s: resource=%s", __FUNCTION__, pWbs->resource);
	pWbs->secure = secure;
	pWbs->portnumber = portnumber;
    CSIO_LOG(eLogLevel_debug, "%s: port=%d  %s", __FUNCTION__, pWbs->portnumber, (pWbs->secure) ? "secure" : "");
    pWbs->urlIsValid = true;
	return true;
}

int wbs_start(int sessId)
{
	Wbs_t *pWbs = (Wbs_t *) &gWbs[sessId];
	pWbs->streamId = sessId;

	if (pWbs->isStarted) {
    	CSIO_LOG(eLogLevel_info, "%s: already started: ignoring request - please stop first", __FUNCTION__);
    	return(-1);
	}
	if (!wbs_useUrl(pWbs))
	{
    	CSIO_LOG(eLogLevel_error, "%s: Failed to start due to invalid url: '%s'", __FUNCTION__, pWbs->url);
	    wbs_SendVideoPlayingStatusMessage(sessId, STREAMSTATE_INVALIDPARAMETER);
	    return(-1);
	}

	pWbs->isPaused = false;
	CSIO_LOG(eLogLevel_debug, "%s: Launching WBS thread for sessionId=%d", __FUNCTION__, sessId);
    if( pthread_create(&(pWbs->wbsTid), NULL, wbsThread, pWbs) )
    {
    	CSIO_LOG(eLogLevel_error, "%s: Failed to start Wbs thread", __FUNCTION__);
    	return(-1);
    }
	CSIO_LOG(eLogLevel_debug, "%s: WBS thread (id=%p) launched for sessionId=%d", __FUNCTION__, pWbs->wbsTid, sessId);
	usleep(10000); // make sure thread is running - may not be needed

    return(0);
}

void wbs_stop(int sessId)
{
	Wbs_t *pWbs = (Wbs_t *) &gWbs[sessId];
	pWbs->streamId = sessId;

	CSIO_LOG(eLogLevel_debug,  "%s: streamId=%d isStarted=%s requestStop=%s", __FUNCTION__, sessId,
			(pWbs->isStarted)?"true":"false", (pWbs->requestStop)?"true":"false");
	if (pWbs->isStarted)
	{
		wbs_stop_connection(pWbs);
		pWbs->isPaused = false;

    	int   iRtn;
    	void* tResults;

    	CSIO_LOG(eLogLevel_debug,  "%s: wait for thread to exit", __FUNCTION__);
        iRtn = pthread_join( pWbs->wbsTid, &tResults );
        CSIO_LOG(eLogLevel_debug,  "%s: thread exited. Status = %d", __FUNCTION__, iRtn );
    	wbs_clear_window(pWbs);    // Now clear window since we have stopped WBS stream
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "%s wbs connection was not started\n", __FUNCTION__);
	}
	wbs_SendVideoPlayingStatusMessage(sessId, STREAMSTATE_STOPPED);
}

void wbs_pause(int sessId)
{
	Wbs_t *pWbs = (Wbs_t *) &gWbs[sessId];

	if (!pWbs->isStarted)
	{
		CSIO_LOG(eLogLevel_error, "%s wbs stream not started - ignoring pause request\n", __FUNCTION__);
		return;
	}
	if (pWbs->isPaused)
	{
		CSIO_LOG(eLogLevel_error, "%s wbs stream already in pause mode - ignoring pause request\n", __FUNCTION__);
		return;
	}
	CSIO_LOG(eLogLevel_error, "%s wbs pausing stream\n", __FUNCTION__);
	pWbs->isPaused = true;
	wbs_SendVideoPlayingStatusMessage(sessId, STREAMSTATE_PAUSED);
}

void wbs_unpause(int sessId)
{
	Wbs_t *pWbs = (Wbs_t *) &gWbs[sessId];

	if (!pWbs->isStarted)
	{
		CSIO_LOG(eLogLevel_error, "%s wbs stream not started - ignoring unpause request\n", __FUNCTION__);
		return;
	}
	if (!pWbs->isPaused)
	{
		CSIO_LOG(eLogLevel_error, "%s wbs stream is not in paused state - ignoring unpause request\n", __FUNCTION__);
		return;
	}
	CSIO_LOG(eLogLevel_error, "%s wbs unpausing stream\n", __FUNCTION__);
	pWbs->isPaused = false;
	wbs_SendVideoPlayingStatusMessage(sessId, STREAMSTATE_STARTED);
}

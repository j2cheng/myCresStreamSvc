/**
 * Copyright (C) 2017 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 *
 * \file        TileDecoder.cpp
 *
 * \brief       WBS Live Stream Decoder
 *
 * \author      Rajesh Hingorani
 *
 * \date        11/29/2017
 *
 * \note        Only minor modifications to code provided by Light Blue Optics Ltd.
 *
 * This module does the actual decoding of the WBS Live Stream
 *
 *
 */

///////////////////////////////////////////////////////////////////////////////

//----------------------------------------------------------------------------
/// \file      TileDecoder.cpp
/// \brief     Example C++ implementation of LiveView client
///
/// \copyright Copyright Light Blue Optics Limited. All rights reserved.
//-----------------------------------------------------------------------------

#include <string.h>
#include <stdint.h>
#include <stdio.h>
#include "csioCommonShare.h"
#include "TileDecoder.h"

#ifdef WHITEBOARD_STREAM_ENABLED
#include "Wbs.h"
static
int curr_logLevel() {return wbs_getLogLevel();}
#else
static
int curr_logLevel() {return eLogLevel_debug; }
#endif

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

static char *decodeWebSocketHeader(char *msg, int msglen)
{
	static char hexBuffer[4096];
	int plen=msg[1]&0x7E;
	if (plen <= 126) {
		sprintf(hexBuffer, "hdrLen=%d FIN=%d MASK=%d payload_len=%d", msglen, (msg[0]&0x80) ? 1 : 0, (msg[1]&0x80) ? 1 : 0,
				(plen < 126) ? plen : (((int) msg[2])*256 + (int) msg[3]));
	}
	else if (plen == 127)
	{
		sprintf(hexBuffer, "hdrLen=%d FIN=%d MASK=%d payload_len=%s", msglen, (msg[0]&0x80) ? 1 : 0, (msg[1]&0x80) ? 1 : 0, "too long");
	}
	else
	{
		sprintf(hexBuffer, "Malformed websocket header: %s", getHexMessage(msg, (msglen < 16) ? msglen : 16));
	}
	return hexBuffer;
}

TileDecoder::TileDecoder(bool websock, bool ownFramebuffer, FBType fbt) :
  m_ws(websock),
  m_ownFb(ownFramebuffer),
  m_fbType(fbt),
  m_fb(0),
  m_width(0),
  m_height(0),
  m_recvLen(0),
  m_sendLen(0),
  m_wsHdrLen(0),
  m_wsRemain(0),
  m_recvBuf(),
  m_sendBuf(),
  m_wsHdrBuf()
{
}

TileDecoder::~TileDecoder()
{
  if (m_ownFb && m_fb) {
    delete [] m_fb;
    m_fb = 0;
  }
}

void TileDecoder::reset(bool websock, bool ownFramebuffer, FBType fbt)
{
  if (m_ownFb && m_fb) {
    delete [] m_fb;
  }
  m_ws       = websock;
  m_ownFb    = ownFramebuffer;
  m_fbType   = fbt;
  m_fb       = 0;
  m_width    = 0;
  m_height   = 0;
  m_recvLen  = 0;
  m_sendLen  = 0;
  m_wsHdrLen = 0;
  m_wsRemain = 0;
}

void TileDecoder::useBuffer(unsigned char * buffer, FBType fbt)
{
  if (m_ownFb && m_fb) {
    delete [] m_fb;
  }
  m_ownFb  = false;
  m_fbType = fbt;
  m_fb     = buffer;
}

int TileDecoder::handleData(unsigned char const * data, int numBytes, int * pFlags)
{
	if (!m_ws) {
		return handleRawData(data, numBytes, pFlags);
	}

	unsigned char const * startPtr = data;
	*pFlags = (m_sendLen > 0) ? FLAG_TXDATA : FLAG_NONE;
	if (m_fb == 0 && m_width > 0) *pFlags |= FLAG_INIT;

	while (numBytes > 0 && *pFlags == FLAG_NONE) {

		if (m_wsRemain < 1) {
			// Read and decode the next WebSocket header
			while (numBytes > 0 && m_wsHdrLen < 2) {
				m_wsHdrBuf[m_wsHdrLen++] = *data++;
				numBytes--;
			}
			int hlen = 2;
			if ((m_wsHdrBuf[1]&0x7F) == 0x7E) hlen += 2;
			if ((m_wsHdrBuf[1]&0x7F) == 0x7F) hlen += 8;
			if (m_wsHdrBuf[1]&0x80) hlen += 4;
			while (numBytes > 0 && m_wsHdrLen < hlen) {
				m_wsHdrBuf[m_wsHdrLen++] = *data++;
				numBytes--;
			}
			if (m_wsHdrLen < hlen) break;
			CSIO_LOG(curr_logLevel(), "%s: WebSocket header: %s", __FUNCTION__, decodeWebSocketHeader((char *)m_wsHdrBuf, m_wsHdrLen));
			m_wsHdrLen = 0; // We have entirely consumed the header; now parse it
			m_wsRemain = (m_wsHdrBuf[1] & 0x7F);
			if (m_wsRemain == 0x7E) {
				m_wsRemain = (m_wsHdrBuf[2]<<8) + m_wsHdrBuf[3];
			}
			else if (m_wsRemain == 0x7F) {
				if (m_wsHdrBuf[2] || m_wsHdrBuf[3] || m_wsHdrBuf[4] || m_wsHdrBuf[5] || (m_wsHdrBuf[6]&0xC0)) {
					CSIO_LOG(eLogLevel_error, "%s: Frame too big!", __FUNCTION__);
					*pFlags = FLAG_ERROR; // Frame much bigger than expected
					break;
				}
				m_wsRemain = (m_wsHdrBuf[6]<<24) + (m_wsHdrBuf[7]<<16) + (m_wsHdrBuf[8]<<8) + m_wsHdrBuf[9];
			}
			CSIO_LOG(curr_logLevel(), "%s: msg frame remaining bytes=%d", __FUNCTION__, m_wsRemain);
			if (m_wsHdrBuf[0] == 0x88) { // Handle Close message
				makeControlMessage(0x8);
				*pFlags = FLAG_TXDATA | FLAG_CLOSE;
			}
			else if (m_wsHdrBuf[0] == 0x89) { // Ping -> Pong (XXX ignore payload!)
				makeControlMessage(0xA);
				*pFlags = FLAG_TXDATA;
			}
			else if ((m_wsHdrBuf[0]&0x70) || (m_wsHdrBuf[1]&0x80)) { // Reserved or mask bits set?
				CSIO_LOG(eLogLevel_error, "%s: Reserved or mask bits set!", __FUNCTION__);
				*pFlags = FLAG_ERROR;
				break;
			}
		}

		int n = m_wsRemain;
		if (numBytes < n) n = numBytes;
		if (n > 0 && (m_wsHdrBuf[0]&0x7F) <= 2 && !(m_wsHdrBuf[1]&0x80)) {
			// Handle data in a Continuation, Text or Binary frame (as binary)
			n = handleRawData(data, n, pFlags);
		}
		data += n;
		numBytes -= n;
		m_wsRemain -= n;
		CSIO_LOG(curr_logLevel(), "%s: used=%d remain=%d", __FUNCTION__, n, m_wsRemain);
	}

	return data - startPtr;
}


int TileDecoder::handleRawData(unsigned char const * data, int numBytes, int * pFlags)
{
  unsigned char const * startPtr = data;
  int f = (m_sendLen > 0) ? FLAG_TXDATA : FLAG_NONE;
  if (m_fb == 0 && m_width > 0) f |= FLAG_INIT;

  // Consume as many bytes as we can until we have something to report to the caller
  while (numBytes > 0 && f == FLAG_NONE) {

    // Streamline the common case where we have a complete Kaptivo message 
    if (m_recvLen == 0 && numBytes >= 4) {
      int msglen = 4 + data[2] + 256*data[3];
      if (numBytes >= msglen) {
        f = handleKaptivoMessage(data, msglen);
        data += msglen;
        numBytes -= msglen;
        continue;
      }
    }

    // Otherwise, build up a Kaptivo message in our receive buffer
    while (m_recvLen < 4 && numBytes > 0) {
      m_recvBuf[m_recvLen++] = *data++;
      numBytes--;
    }
    if (m_recvLen >= 4) {
      int msglen = 4 + m_recvBuf[2] + 256*m_recvBuf[3];
     int n2copy = msglen - m_recvLen;
      if (n2copy > numBytes) n2copy = numBytes;
      if (n2copy > 0) {
        memcpy(m_recvBuf + m_recvLen, data, n2copy);
        m_recvLen += n2copy;
        data += n2copy;
        numBytes -= n2copy;
      }
      CSIO_LOG(curr_logLevel(), "Asssembling Kaptivo message of length %d: have %d bytes", msglen, m_recvLen);
      if (m_recvLen >= msglen) {
        f = handleKaptivoMessage(m_recvBuf, msglen);
        m_recvLen = 0;
      }
    }
  }

  *pFlags = (f & FLAG_ERROR) ? FLAG_ERROR : f;
  return (data - startPtr);
}


int TileDecoder::handleKaptivoMessage(unsigned char const * ptr, int msglen)
{
  CSIO_LOG(curr_logLevel(), "Kaptivo message (len=%d): %s", msglen, getHexMessage((char *)ptr, (msglen > 20) ? 20 : msglen));
  // Sanity check header
  if (msglen < 4 || ptr[0] != 0x4B || ptr[1] < 0x60 || ptr[1] > 0x7F) {
	  CSIO_LOG(eLogLevel_error, "%s: Header sanity check failed!", __FUNCTION__);
	  return FLAG_ERROR;
  }

  // Handle message by type. Some message types (e.g. StartUpdate) are ignored
  int msgtype = ptr[1];
  switch (msgtype) {

  case 0x60: // ServerInit
    {
      if (msglen < 16 || ptr[5]!=0 || ptr[6]!=0 || ptr[7]!=0) {
    	  CSIO_LOG(eLogLevel_error, "%s: Invalid length or bytes in ServerInit message!", __FUNCTION__);
    	  return FLAG_ERROR;
      }
      int w = ptr[12] + 256*ptr[13];
      int h = ptr[14] + 256*ptr[15];
      if (w == 0 || h == 0 || (m_width != 0 && (w != m_width || h != m_height))) {
    	  CSIO_LOG(eLogLevel_error, "%s: Invalid width %d or height %d!", __FUNCTION__, w, h);
    	  return FLAG_ERROR;
      }
      if (w == m_width && h == m_height) return FLAG_NONE; // ignore duplicate
      m_width  = w;
      m_height = h;
      if (m_ownFb) {
        if (m_fb != 0) delete [] m_fb;
        m_fb = new unsigned char [((m_fbType==FBTYPE_RGBA)?4:3)*w*h];
      }
      memset(m_fb, 0x81, ((m_fbType==FBTYPE_RGBA)?4:3)*w*h); // clear framebuffer to all-grey
      makeKaptivoMessage(0x50, 8);           // generate a START message (no seqnum)
      return FLAG_INIT|FLAG_TXDATA;
    }
    break;

  case 0x62: // EndUpdate
    if (msglen >= 8) {
      makeKaptivoMessage(0x52, 4, ptr+4); // Acknowledge the update
      return FLAG_FRAME|FLAG_TXDATA;
    }
    break;

  case 0x63: // Connection Rejected
    return FLAG_REJECTED;
    break;

  case 0x70: case 0x71: case 0x72: // TileData
    {
      if (m_width > 0 && m_height > 0 && m_fb != 0 && msglen > 6) {
        int tx = TILE_SIZE*ptr[4];
        int ty = TILE_SIZE*ptr[5];
        ptr += 6;
        msglen -= 6;
        while (msglen > 0 && tx < m_width && ty < m_height) {
          int tw = (tx+TILE_SIZE <= m_width)  ? TILE_SIZE : m_width-tx;
          int th = (ty+TILE_SIZE <= m_height) ? TILE_SIZE : m_height-ty;
          int n = decodeTile(msgtype & 3, ptr, msglen, tx, ty, tw, th);
          ptr += msglen - n;
          msglen = n;
          if ((tx += TILE_SIZE) >= m_width) {
            tx = 0;
            ty += TILE_SIZE;
          }
        }
      }
    }

  default: /* do nothing */ ;
  }

  return FLAG_NONE;
}

void TileDecoder::makeKaptivoMessage(int msgtype, int length, unsigned char const * src)
{
  if (m_ws) {
    // Generate a pseudo-random mask (mandatory as we are the WebSocket client).
    // Mask does not need to be super-secure as there is no untrusted JavaScript
    // (hooray!) and we will normally be wrapping the whole thing in TLS.
    static uint32_t randomish = 0X0BEEF;
    randomish = randomish*1103515245 + 12345;

    // WebSocket header
    memset(m_sendBuf, 0, 10 + length);
    m_sendBuf[0] = 0x85;
    m_sendBuf[1] = 0x80 + 4 + length;
    m_sendBuf[2] = randomish>>24;
    m_sendBuf[3] = randomish>>16;
    m_sendBuf[4] = randomish>>8;
    m_sendBuf[5] = randomish;

    // LiveView frame header, masked
    m_sendBuf[6] = m_sendBuf[2] ^ 0x4B;
    m_sendBuf[7] = m_sendBuf[3] ^ msgtype;
    m_sendBuf[8] = m_sendBuf[4] ^ length;
    m_sendBuf[9] = m_sendBuf[5];

    // Payload, masked
    if (src) {
      for(int i = 0; i < length; ++i) m_sendBuf[10+i] = src[i] ^ m_sendBuf[2+(i&3)];
    }
    else {
      for(int i = 0; i < length; ++i) m_sendBuf[10+i] = m_sendBuf[2+(i&3)];
    }
    m_sendLen = 10 + length;
  }

  else {
    // Native LiveView frame
    m_sendBuf[0] = 0x4B;
    m_sendBuf[1] = msgtype;
    m_sendBuf[2] = length;
    m_sendBuf[3] = 0;
    if (src) memcpy(m_sendBuf+4, src, length);
    else if (length > 0) memset(m_sendBuf+4, 0, length);
    m_sendLen = 4 + length;
  }
}

void TileDecoder::makeControlMessage(int opcode)
{
  m_sendBuf[0] = 0x80 | opcode;
  m_sendBuf[1] = 0x80; // Masking is pointless for empty messages, but may be mandatory(?)
  m_sendBuf[2] = 0xF0;
  m_sendBuf[3] = 0x0F;
  m_sendBuf[4] = 0x55;
  m_sendBuf[5] = 0xAA;
  m_sendLen = 6;
}

int TileDecoder::decodeTile(int mode, unsigned char const * ptr, int numBytes, int tx, int ty, int tw, int th)
{
  uint32_t tdata[TILE_SIZE*TILE_SIZE];
  uint32_t palette[12];
  for(int i = 0; i < 12; ++i) palette[i] = 0;
  int palWrPos = 0;
  int area     = tw*th;
  int pos      = 0;
  unsigned char const * endPtr = ptr + numBytes;

  while (pos < area && ptr < endPtr) {

    // Decode run header
    int rt = *ptr++;
    int rl = (rt>>4) & 0xF; // run length
    rt &= 0xF;              // run type
    if (rl == 15) {
      if (ptr >= endPtr) return 0;
      rl += *ptr++;
      if (rl >= 267) {
        if (ptr >= endPtr) return 0;
        rl += 4*(*ptr++);
      }
    }
    rl = (rl == 0 || rl+pos > area) ? area : rl+pos; // rl is now the end position of the run

    // Decode run content
    if (rt < 14) {
      uint32_t rgb = 0xFFFFFF;
      if (rt < 12) {
        rgb = palette[rt];
      }
      else if (rt > 12) {
        if (ptr+mode >= endPtr) return 0;
        switch (mode) {
        case 0:
          rgb = 0x010101 * (*ptr++);
          break;
        case 1:
          rgb = 0x11*(ptr[0]&15) + 0x1100*(ptr[0]>>4) + 0x110000*(ptr[1]&15);
          ptr += 2;
          break;
        default:
          rgb = ptr[0] + (ptr[1]<<8) + (ptr[2]<<16);
          ptr += 3;
        }
        palette[palWrPos] = rgb;
        if (++palWrPos >= 12) palWrPos = 0;
      }
      while (pos < rl) {
        tdata[pos++] = rgb;
      }
    }
    else if (rt == 14) {
      while (pos < rl) {
        tdata[pos] = (pos < tw) ? 0xFFFFFF : tdata[pos-tw];
        ++pos;
      }
    }
    else {
      switch (mode) {
      case 0:
        if (ptr+(rl-pos) > endPtr) return 0;
        while (pos < rl) {
          tdata[pos++] = 0x010101 * (*ptr++);
        }
        break;
      case 1:
        if (ptr + (((rl-pos)*3+1)>>1) > endPtr) return 0;
        while (pos < rl) {
          tdata[pos] = 0x11*(ptr[0]&15) + 0x1100*(ptr[0]>>4) + 0x110000*(ptr[1]&15);
          ptr += 2;
          if (++pos < rl) {
            tdata[pos++] = 0x11*(ptr[-1]>>4) + 0x1100*(ptr[0]&15) + 0x110000*(ptr[0]>>4);
            ptr++;
          }
        }
        break;
      default:
        if (ptr + (rl-pos)*3 > endPtr) return 0;
        while (pos < rl) {
          tdata[pos++] = ptr[0] + (ptr[1]<<8) + (ptr[2]<<16);
          ptr += 3;
        }
      }
    }
  }

  uint32_t const * sp = tdata;
  int ty1 = ty + th;
  for(; ty < ty1; ++ty) {
    if (m_fbType == FBTYPE_RGBA) {
      unsigned char * dp = m_fb + 4*(m_width*ty+tx);
      for(int x = 0; x < tw; ++x) {
        uint32_t u = *sp++;
        *dp++ = u;
        *dp++ = u>>8;
        *dp++ = u>>16;
        *dp++ = 0xFF;
      }
    }
    else if (m_fbType == FBTYPE_RGB) {
      unsigned char * dp = m_fb + 3*(m_width*ty+tx);
      for(int x = 0; x < tw; ++x) {
        uint32_t u = *sp++;
        *dp++ = u;
        *dp++ = u>>8;
        *dp++ = u>>16;
      }
    }
    else { 
      unsigned char * dp = m_fb + 3*(m_width*ty+tx);
      for(int x = 0; x < tw; ++x) { // Support BGR for the benefit of OpenCV
        uint32_t u = *sp++;
        *dp++ = u>>16;
        *dp++ = u>>8;
        *dp++ = u;
      }
    }
  }

  return endPtr - ptr; // NB: returns the number of bytes remaining (not the number consumed)
}

void TileDecoder::solicitKeepAlive() {
  if (m_sendLen == 0) {
    if (m_ws) {
      makeControlMessage(0xA);
    }
    else {
      makeKaptivoMessage(0x5F, 0);
    }
  }
}

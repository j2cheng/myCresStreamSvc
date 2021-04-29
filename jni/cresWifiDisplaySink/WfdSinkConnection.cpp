/**
 * Copyright (C) 2016 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 *
 * \file        WfdSinkConnection.cpp
 *
 * \brief
 *
 * \author      John Cheng
 *
 * \date        12/25/2018
 *
 * \note
 *
 *
 * \todo
 */
#include <net/if.h>
#include <errno.h>
#include <arpa/inet.h> //for inet_addr
#include "WfdSinkConnection.h"

static bool makeNonBlocking(int sock)
{
    int flags = fcntl(sock, F_GETFL, 0);
    if (flags < 0)
    {
        CSIO_LOG(eLogLevel_error, "WfdSinkConnection: unable to put socket into non-blocking mode ");
    }
    else
    {
        flags = flags | O_NONBLOCK;

        if(fcntl(sock, F_SETFL, flags) == 0)
        {
            CSIO_LOG(eLogLevel_extraVerbose,"makeNonBlocking: Success, the socket[%d] is now set to non-blocking mode.",sock);
            return true;
        }
        else
        {
            CSIO_LOG(eLogLevel_error,"makeNonBlocking: F_SETFL failed. Unable to put socket into non-blocking mode.");
        }
    }

    return false;
}

//Note: we call this function after \r\n\r\n reached
static int getContentLength(unsigned char* startPtr,unsigned char* endPtr)
{
    int buffSize = endPtr - startPtr;
    char* cpBuff = new char[buffSize+1];
    int returnValue = 0;

    if(cpBuff)
    {
        memcpy(cpBuff,startPtr,buffSize);
        cpBuff[buffSize] = '\0';

        //Note: case insensitive search
        char* contentPtr = strcasestr( (char*)cpBuff, "Content-Length:" );

        if( contentPtr )
        {
            contentPtr += 15;
            sscanf(contentPtr, "%u", &returnValue);
        }

        delete [] cpBuff;
        cpBuff = NULL;
    }

    return returnValue;
}
WfdRTSPSinkClient::WfdRTSPSinkClient(wfdSinkStMachineClass* parent):
sourcePortNum(DEFAULT_SOURCE_RTSP_PORT),
m_sourceAddress(),
fReceivedBytesProcessed(0),
fReceiveBufferBytesLeft(RECEIVE_BUFFER_SIZE),
fContentLength(0),
fLastCRLFCRLF(fReceiveBuffer),
m_parent(NULL),
m_debugLevel(eLogLevel_debug),
m_sock(-1)
{
    m_parent = parent;

    m_sourceAddress.clear();

    memset(fReceiveBuffer,0,TOTAL_BUFFER_SIZE);
    memset(fTransmitBuffer,0,TOTAL_BUFFER_SIZE);

    resetReceivedBuffer();
}
WfdRTSPSinkClient::~WfdRTSPSinkClient()
{
    if(m_sock >= 0)
        close(m_sock);

    CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: ~WfdRTSPSinkClient\n");
}

void WfdRTSPSinkClient::setSourceAddrPort(const char* pUrl,int port)
{
    if(pUrl)
    {
        sourcePortNum = port;

        if(strlen(pUrl) < SOURCE_ADDR_BUF_SIZE)
            m_sourceAddress = pUrl;
        else
            CSIO_LOG(m_debugLevel, "ERROR:");
    }

    CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "WfdRTSPSinkClient: setSourceAddrPort is done,m_sourceAddress(%s),sourcePortNum[%d]\n",
             m_sourceAddress.c_str(),sourcePortNum);
}
void WfdRTSPSinkClient::resetSocket()
{
    if(m_sock >= 0)
        close(m_sock);

    m_sock = -1;
}
int WfdRTSPSinkClient::openConn()
{
    int newSocket = socket(AF_INET, SOCK_STREAM, 0);

    CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient:m_getMiceSession[%d],newSocket[%d]\n",m_parent->m_getMiceSession(),newSocket);

    if (m_parent->m_getMiceSession() && newSocket >= 0)
    {
        const char *opt = m_parent->m_parent->getLocIPName(m_parent->getId());        

        /* bind to an address */
        struct sockaddr_in localaddr = {0};
        localaddr.sin_family    = AF_INET;
        localaddr.sin_port  = htons(0);//any port
        localaddr.sin_addr.s_addr = inet_addr(opt);
        int rc = bind(newSocket, (struct sockaddr*) &localaddr, sizeof(struct sockaddr_in));
        CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: openConn bind[%s] return: %d",opt,rc);
    }

    if (!makeNonBlocking(newSocket) || newSocket < 0)
    {
        if(newSocket >= 0)
            close(newSocket);

        CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: openConn unable to create stream socket: ");
        return -1;
    }
    else
    {
        sockaddr_in sinRemote;
        memset((void*)&sinRemote,0,sizeof(sinRemote));
        sinRemote.sin_family = AF_INET;
        sinRemote.sin_addr.s_addr = inet_addr(m_sourceAddress.c_str());;
        sinRemote.sin_port = htons( sourcePortNum );

        CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: m_sourceAddress:%s sourcePortNum:%d",m_sourceAddress.c_str(), sourcePortNum);
        CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: newSocket:%d,port[%d]",
                 newSocket,sinRemote.sin_port);
        
        if( connect(newSocket, (struct sockaddr*) &sinRemote, sizeof(sockaddr_in)) != 0 )
        {
            if (errno == EINPROGRESS || errno == EWOULDBLOCK)
            {
                if(errno == EINPROGRESS)
                    CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: errno:EINPROGRESS");
                else if(errno == EWOULDBLOCK)
                    CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: errno:EWOULDBLOCK");

                //setup poll fds
                pollfd pfd;
                pfd.fd = newSocket;
                pfd.events = POLLOUT;           // Monitor sock for output. (POLLIN);

                int ret = poll(&pfd, 1, 1000);   //only 1 socket, timeout 1000ms

                if ( ret == -1 )
                {
                    // report error and abort
                    close(newSocket);
                    CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: connect failed: -1");
                    return -1;
                }
                else if ( ret == 0 )
                {
                    // timeout; no event detected
                    close(newSocket);
                    CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: connect failed: timeout after 1 sec");
                    return -1;
                }
                else
                {
                    // If we detect the event, zero it out so we can reuse the structure
                    //if ( pfd.revents & POLLIN )  pfd.revents = 0;// input event on sock
                    CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: connect poll returned: %d",ret);
                    if ( pfd.revents & POLLOUT )
                    {
                        pfd.revents = 0; // output event on sock
                    }
                    else
                    {
                        CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: ERROR: why POLLOUT event is missing?");
                    }
                }
            }
            else
            {
                close(newSocket);
                CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: connect failed with error:%d ",errno);
                return -1;
            }
        }
    }

    m_sock = newSocket;
    CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: newSocket:%d  returned",newSocket);
    return newSocket;
}
void WfdRTSPSinkClient::resetReceivedBuffer()
{
    fReceivedBytesProcessed = 0;
    fContentLength = 0;

    fReceiveBufferBytesLeft = RECEIVE_BUFFER_SIZE;

    fLastCRLFCRLF = &fReceiveBuffer[0];

    //NOTE:Never clear buffer here!!!
}

/*****
 * return :
 *    0   -- error ignored
 *    -1  -- need to close the socket
 *    > 0 -- received data
 *
 * ***/
int  WfdRTSPSinkClient::readSocket()
{
    struct sockaddr_in clientAddr;
    socklen_t addrLen = sizeof(sockaddr_in);
    int read_size = 0;

    if(m_sock >= 0)
    {
        read_size = recvfrom(m_sock,&fReceiveBuffer[fReceivedBytesProcessed],fReceiveBufferBytesLeft,0,
                (struct sockaddr *)&clientAddr,&addrLen);

        if(read_size < 0)
        {
            if ( (errno == EAGAIN) || (errno == EWOULDBLOCK) )
            {
                /* Timed out */
                CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: sock[%d] recv timed out",m_sock);
            }
            else if (errno == EINTR)
            {
                /* Interrupted by a signal; ignore and continue */
                CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: sock[%d] signal interrupt, ignored",m_sock);
            }
            else
            {
                CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: socket[%d] unknown error",m_sock);

                return -1;
            }

            return 0;//return with no error
        }
    }
    else
    {
        CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: ERROR: readSocket sock[%d]",m_sock);
    }

    if (read_size == 0)
    {
        /* Graceful disconnection by remote */
        CSIO_LOG(m_debugLevel, "WfdRTSPSinkClient: WfdRTSPSinkClient sock[%d] disconnection---- by remote----",m_sock);

        return -1;
    }
    else//(read_size > 0)
    {
        //CSIO_LOG(m_debugLevel, "sock[%d][%s] Client read:%d",
        //        m_sock,inet_ntoa(clientAddr.sin_addr),read_size);

        if(read_size <= fReceiveBufferBytesLeft)
            return handleReceivedBytes(read_size);
        //else if this happens, we might have to terminate here
    }

    return 0;
}

int WfdRTSPSinkClient::handleReceivedBytes(int newbytes)
{
    int packageSize = 0;
    int numBytesRemaining = 0;

    do{
        if(fContentLength == 0)
        {
            bool endOfMsg = false;
            unsigned char* ptr = &fReceiveBuffer[fReceivedBytesProcessed];
            unsigned char* tmpPtr = fLastCRLFCRLF;
            unsigned char* endPtr ;

            //Note: check at least 4 bytes
            endPtr = (&ptr[newbytes] - 3); 

            while (tmpPtr < endPtr)
            {
                if (*tmpPtr == '\r' || *tmpPtr == '\n')
                {
                    if (*(tmpPtr+1) == '\r' || *(tmpPtr+1) == '\n')//found crlf
                    {
                        if (*(tmpPtr+2) == '\r' || *(tmpPtr+2) == '\n')
                        {
                            if (*(tmpPtr+3) == '\r' || *(tmpPtr+3) == '\n')
                            {
                                fLastCRLFCRLF = tmpPtr + 4;
                                endOfMsg = true;
                                break;
                            }
                        }
                    }
                }
                ++tmpPtr;
            }
            
            fReceiveBufferBytesLeft -= newbytes;
            fReceivedBytesProcessed += newbytes;

            //Note: we break the while loop if we did not find the end of msg
            if (!endOfMsg) break;

            //Note: we call getContentLength() after '\r\n\r\n' reached
            fContentLength = getContentLength(fReceiveBuffer,fLastCRLFCRLF);
        }
        else
        {
            fReceiveBufferBytesLeft -= newbytes;
            fReceivedBytesProcessed += newbytes;
        }

        unsigned requestSize = (fLastCRLFCRLF - fReceiveBuffer) + fContentLength;
        numBytesRemaining = fReceivedBytesProcessed - requestSize;

        if(numBytesRemaining == 0)
        {
            //save Tx processed bytes,use requestSize
            memcpy(fTransmitBuffer,fReceiveBuffer,requestSize);
            fTransmitBuffer[requestSize] = '\0';

            if(m_parent)
                m_parent->processPackets(requestSize,(char*)fTransmitBuffer);

            packageSize = requestSize;

            resetReceivedBuffer();
        }
        else if (numBytesRemaining > 0)
        {
            //save Tx processed bytes,use requestSize
            memcpy(fTransmitBuffer,fReceiveBuffer,requestSize);
            fTransmitBuffer[requestSize] = '\0';

            if(m_parent)
                m_parent->processPackets(requestSize,(char*)fTransmitBuffer);

            packageSize = requestSize;

            resetReceivedBuffer();
            memmove(fReceiveBuffer, &fReceiveBuffer[requestSize], numBytesRemaining);
            newbytes = numBytesRemaining;
        }

        //numBytesRemaining < 0 is due to fContentLength
    } while (numBytesRemaining > 0);

    return packageSize;
}

int WfdRTSPSinkClient::sendDataOut(char* msg, int size)
{
    int result = -1;

    CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel), "WfdRTSPSinkClient: sendDataout size[%d]",size);

    if(m_sock >= 0 && msg)
    {
        result = send(m_sock, msg, size, 0);

        CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel), "WfdRTSPSinkClient to m_sock[%d],size[%d],result[%d]",
                 m_sock,size,result);
    }

    CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel), "WfdRTSPSinkClient: sendDataout result[%d]",result);

    return 0;
}

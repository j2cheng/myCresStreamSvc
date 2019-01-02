#ifndef _WFD_SINK_CONNECTION_H_
#define _WFD_SINK_CONNECTION_H_

#include "WfdCommon.h"
#include "WfdSinkState.h"

#define TOTAL_BUFFER_SIZE    3000 // for msg buffer
#define RECEIVE_BUFFER_SIZE  2500
#define MAX_STRING_SIZE      200
#define SOURCE_ADDR_BUF_SIZE 200
#define DEFAULT_SEQ_NUMBER_INIT 1000
#define KEEP_ALIVE_TIMEOUT_IN_SEC   30 //15*2 seconds
#define DEFAULT_SOURCE_RTSP_PORT 7236  //else within 49152 to 65535

class wfdSinkStMachineClass;

class WfdRTSPSinkClient
{
public:
    WfdRTSPSinkClient(wfdSinkStMachineClass* parent);
    ~WfdRTSPSinkClient();

    void setSourceAddrPort(const char* pUrl,int port);
    int openConn();
    void resetReceivedBuffer();
    void resetSocket() { if(m_sock >= 0) close(m_sock);}
    int getSocket() { return m_sock;}

    int handleReceivedBytes(int newbytes);
    int  readSocket();
    int sendDataOut(char* msg, int size);

    void setDebugLevel(int level) { m_debugLevel = level; }
    int  getDebugLevel() { return m_debugLevel; }

    int sourcePortNum;
    std::string m_sourceAddress;

    unsigned char fReceiveBuffer[TOTAL_BUFFER_SIZE];
    unsigned char fTransmitBuffer[TOTAL_BUFFER_SIZE];

    int fReceivedBytesProcessed ;
    int fReceiveBufferBytesLeft ,fContentLength;
    unsigned char* fLastCRLFCRLF ;

    wfdSinkStMachineClass* m_parent;

    int   m_debugLevel,m_sock;
};

#endif

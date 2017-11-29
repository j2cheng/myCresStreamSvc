/**
 * Copyright (C) 2017 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 *
 * \file        TileDecoder.h
 *
 * \brief       WBS Live Stream Decoder header file
 *
 * \author      Rajesh Hingorani
 *
 * \date        11/29/2017
 *
 * \note        Only minor modifications to code provided by Light Blue Optics Ltd.
 *
 *
 */

 ///////////////////////////////////////////////////////////////////////////////

//----------------------------------------------------------------------------
/// \file      TileDecoder.h
/// \brief     Example C++ implementation of LiveView client
///
/// \copyright Copyright Light Blue Optics Limited. All rights reserved.
//-----------------------------------------------------------------------------

#pragma once

class TileDecoder {
 public:

  // Flags representing conditions raised by handleData() that require
  // some action by the caller (for instance, allocate an image buffer,
  // display a frame or transmit some data back to the server).
  // If multiple bits are set, they should be handled in LSB-to-MSB order.
  enum {
    FLAG_NONE   = 0,
    FLAG_INIT   = 1,  // we have discovered the dimensions of the frame
    FLAG_FRAME  = 2,  // we have decoded a framebuffer update
    FLAG_TXDATA = 4,  // there are bytes ready to transmit
    FLAG_CLOSE  = 8,  // a WebSocket "close" was received and replied to
    FLAG_ERROR  = 16  // fatal error; the connection should be closed
  };

  // Framebuffer formats. Currently support BGR888, RGB888 and RGBA8888.
  enum FBType {
    FBTYPE_BGR,
    FBTYPE_RGB,
    FBTYPE_RGBA
  };

  // Constructor. If "websock" is set, TileDecoder will parse and generate
  // WebSocket message frame headers, detect Close, and respond to Ping
  // with Pong (but it will *not* perform HTTP-style handshaking or TLS).
  // If "ownFramebuffer" is set, TileDecoder will automatically allocate
  // a suitable frame buffer (otherwise one must be managed by the caller).
  TileDecoder(bool websock, bool ownFramebuffer, FBType fbt = FBTYPE_RGB);

  // Destructor. If the frame buffer is "owned" then it will be freed.
  ~TileDecoder();

  // Re-use an existing TileDecoder for a new session; roughly equivalent
  // to destruction followed by construction in place. All state is reset.
  void reset(bool websock, bool ownFramebuffer, FBType fbt = FBTYPE_RGB);
  void reset() { reset(m_ws, m_ownFb, m_fbType); }

  // Get dimensions of the frame buffer. Available once FLAG_INIT has been seen.
  bool dimensionsAvailable() const { return m_width > 0; }
  int getWidth() const  { return m_width;  }
  int getHeight() const { return m_height; }

  // Set an external frame buffer pointer (not owned by TileDecoder).
  void useBuffer(unsigned char * buffer, FBType fbt = FBTYPE_RGB);

  // Get a pointer to the frame buffer. Returns NULL if not yet allocated.
  unsigned char * getBuffer() { return m_fb; }
  unsigned char const * getBuffer() const { return m_fb; }
  FBType getFBType() const { return m_fbType; }

  // Consume some data received from the connection to the server.
  // Returns the number of bytes consumed, which may be less than numBytes,
  // and sets flags to indicate conditions that the caller must handle.
  int handleData(unsigned char const * data, int numBytes, int * pFlags);

  // If there was nothing to send, but some communications layer requires a
  // keep-alive (as our embedded web-server currently does!), this will make
  // a Pong (in WebSocket mode) or a meaningless LiveView message (otherwise).
  // [TileDecoder does not schedule keep-alives; an external timer task may be
  // needed. Avoid calling concurrently with handleData() or getDataToSend().]
  void solicitKeepAlive();

  // Get data to transmit. Call these after handledData() has set FLAG_TXDATA
  // or after calling solicitKeepAlive(). getDataToSend() will clear the send
  // buffer; you must transmit everything before the next call to handleData().
  int numBytesToSend() const { return m_sendLen; }
  unsigned char const * getDataToSend() { m_sendLen = 0; return m_sendBuf; }

 private:

  bool            m_ws;
  bool            m_ownFb;
  FBType          m_fbType;
  unsigned char * m_fb;
  int             m_width;
  int             m_height;
  int             m_recvLen;
  int             m_sendLen;
  int             m_wsHdrLen;
  int             m_wsRemain;
  unsigned char   m_recvBuf[65544];
  unsigned char   m_sendBuf[24];
  unsigned char   m_wsHdrBuf[16];

  int handleRawData(unsigned char const * data, int numBytes, int * pFlags);
  int handleKaptivoMessage(unsigned char const * ptr, int msglen);
  int decodeTile(int mode, unsigned char const * ptr, int bytes, int tx, int ty, int tw, int th);
  void makeKaptivoMessage(int msgtype, int length, unsigned char const * src = 0);
  void makeControlMessage(int opcode);

  static const int TILE_SIZE   = 32;

  // Copy constructor and assignment operator are forbidden.
  TileDecoder(TileDecoder const & rhs);
  TileDecoder & operator=(TileDecoder const & rhs);
};

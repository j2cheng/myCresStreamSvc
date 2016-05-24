/**
 * Copyright (C) 2016 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 * 
 * \file        cresstreamctrl_jni.c
 *
 * \brief       Java <--> native interface code.
 *
 * \author      Pete McCormick
 *
 * \date        5/23/2016
 *
 * \note        Java <--> C interface functions used by CresStreamCtrl.java
 * 
 * 				This library must exist so that the CresStreamCtrl object in Java
 * 				can access the Product_Information structure in libCsioProdInfo.so
 */

///////////////////////////////////////////////////////////////////////////////

#include <jni.h>
#include "CresStreamCtrl.h"

// In csioCommonShare.h, Product_Information structure uses "bool", 
// but that doesn't exist in C, only C++.
#ifndef __cplusplus
	typedef int bool;
#endif

#include "csioCommonShare.h"

int did_init;
CSIOSettings* currentSettingsDB;

///////////////////////////////////////////////////////////////////////////////

JNIEXPORT jboolean JNICALL Java_com_crestron_txrxservice_CresStreamCtrl_nativeHaveExternalDisplays(JNIEnv *env, jobject thiz)
{
	if(!did_init)
	{
		did_init = 1;
	    currentSettingsDB = (CSIOSettings*)malloc(sizeof(CSIOSettings));
		csio_setup_product_info(0);
	}

	if(product_info()->stream_display_bitmask & 0xfffffffe)
	{
		return JNI_TRUE;
	}
	
	return JNI_FALSE;
}

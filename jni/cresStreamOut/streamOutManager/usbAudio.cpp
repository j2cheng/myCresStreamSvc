/**
 * Copyright (C) 2016 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 * 
 * \file        cresStreamOutManager.cpp
 *
 * \brief       Implementation of stream out
 *
 * \author      John Cheng
 *
 * \date        7/5/2016
 *
 * \note
 */

///////////////////////////////////////////////////////////////////////////////

#include "cresStreamOutManager.h"
#include "usbAudio.h"
#include <string.h>

#ifdef HAS_TINYALSA

#ifndef ARRAY_SIZE
#define ARRAY_SIZE(a) (sizeof(a) / sizeof((a)[0]))
#endif

int UsbAudio::pcm_mask_test(struct pcm_mask *m, unsigned int index)
{
    const unsigned int bitshift = 5; /* for 32 bit integer */
    const unsigned int bitmask = (1 << bitshift) - 1;
    unsigned int element;
    element = index >> bitshift;
    if (element >= ARRAY_SIZE(m->bits))
        return 0; /* for safety, but should never occur */
    return (m->bits[element] >> (index & bitmask)) & 1;
}

// returns number of samples available on device
unsigned int UsbAudio::usb_audio_avail(pcm *pcm_device)
{
    unsigned int avail;
    struct timespec tstamp;

    if (pcm_get_htimestamp(pcm_device, &avail, &tstamp) < 0)
    {
        CSIO_LOG(eLogLevel_error, "UsbAudio: usb_audio_avail(): pcm_get_htimestamp failed -- timestamp=%ld.%ld avail=%d", tstamp.tv_sec, tstamp.tv_nsec, avail);
        avail = 1000000; // to force a pcm_read in usb_audio_avail - at startup if we return 0 here nothing happens
    }
    else
    {
        CSIO_LOG(eLogLevel_verbose, "UsbAudio: usb_audio_avail(): timestamp=%ld.%ld avail=%d", tstamp.tv_sec, tstamp.tv_nsec, avail);
        
        
        GstClockTime timestamp = GST_TIMESPEC_TO_TIME (tstamp);

        CSIO_LOG (eLogLevel_verbose, "UsbAudio: usb_audio_avail(): timestamp : %" GST_TIME_FORMAT", delay %u", GST_TIME_ARGS (timestamp), avail);
    }
    return avail;
}

void UsbAudio::usb_audio_get_samples(pcm *pcm_device, void *data, int size, int channels, GstClockTime *timestamp, GstClockTime *duration) {
    
    guint64 nsamples = (guint64)(size/(channels*2));
    int error;

    *duration = gst_util_uint64_scale(nsamples, GST_SECOND, 48000);
    if ((error=pcm_read(pcm_device, data, size)) != 0)
    {
        CSIO_LOG(eLogLevel_error, "UsbAudio: get_pcm_samples(): could not get %d bytes error=%d", size, error);
    } else {
        CSIO_LOG(eLogLevel_verbose, "UsbAudio: get_pcm_samples(): read %d bytes,m_usb_audio_sample[%llu],nsamples[%llu]", size,m_usb_audio_sample,nsamples);
    }
    *timestamp = gst_util_uint64_scale(m_usb_audio_sample, GST_SECOND, 48000);
    m_usb_audio_sample += nsamples;
    CSIO_LOG(eLogLevel_verbose, "UsbAudio: get_pcm_samples(): size=%d nsamples=%llu timestamp=%llu duration=%llu m_usb_audio_sample=[%llu]", size, nsamples, *timestamp, *duration,m_usb_audio_sample);


    CSIO_LOG (eLogLevel_verbose, "gst_util_uint64_scale timestamp : %" GST_TIME_FORMAT, GST_TIME_ARGS (*timestamp));
}

// returns true if successful
bool UsbAudio::configure()
{
    if (m_device == NULL)
    {
        // open USB pcm audio device
        struct pcm_config config;
        memset(&config, 0, sizeof(config));
        config.channels = m_audioChannels;
        config.rate = m_audioSamplingRate;
        config.period_size = 1024;
        config.period_count = 4;
        config.format = m_audioPcmFormat;
        config.start_threshold = 0;
        config.stop_threshold = 0;
        config.silence_threshold = 0;

        m_device = pcm_open(m_pcm_card_idx, m_pcm_device_idx, PCM_IN | PCM_MONOTONIC, &config);
        if (!m_device || !pcm_is_ready(m_device))
        {
            CSIO_LOG(eLogLevel_error, "Unable to open USB audio PCM device (%s)\n", pcm_get_error(m_device));
            if (m_device)
            {
                pcm_close(m_device);
                m_device = NULL;
            }
            return false;
        }
        else
        {
            CSIO_LOG(eLogLevel_info, "USB audio PCM device card=%d device=%d is open for capture\n", m_pcm_card_idx, USB_PCM_DEVICE);
            m_usb_audio_sample = 0;
            int bufsize = pcm_frames_to_bytes(m_device, pcm_get_buffer_size(m_device));
            CSIO_LOG(eLogLevel_info, "USB audio PCM device channels = %d\n", config.channels);
            CSIO_LOG(eLogLevel_info, "USB audio PCM device rate = %d\n", config.rate);
            CSIO_LOG(eLogLevel_info, "USB audio PCM device period size = %d\n", config.period_size);
            CSIO_LOG(eLogLevel_info, "USB audio PCM device period count = %d\n", config.period_count);
            CSIO_LOG(eLogLevel_info, "USB audio PCM device format = %d\n", config.format);
            CSIO_LOG(eLogLevel_info, "USB audio PCM device buffer size = %d (%d)\n", bufsize, pcm_get_buffer_size(m_device));
        }
    }
    return true;
}

// returns true if successful
bool UsbAudio::getAudioParams()
{
	bool rv = false;
	CSIO_LOG(eLogLevel_info, "--UsbAudio: calling pcm_params_get for card %d device %d", m_pcm_card_idx, m_pcm_device_idx);
	m_params = pcm_params_get(m_pcm_card_idx, m_pcm_device_idx, PCM_IN);
	CSIO_LOG(eLogLevel_info, "--UsbAudio: got pcm_params_get for card %d device %d params=%p", m_pcm_card_idx, m_pcm_device_idx, m_params);
	char pcm_param_string[4096];
	if (!m_params) {
		CSIO_LOG(eLogLevel_error, "--UsbAudio: no audio device");
		return false;
	} else {
		if (pcm_params_to_string(m_params, pcm_param_string,
				sizeof(pcm_param_string)) > ((int) sizeof(pcm_param_string))) {
			CSIO_LOG(eLogLevel_error,
					"--UsbAudio: failed to get pcm params");
		} else {
			CSIO_LOG(eLogLevel_info,
					"--UsbAudio: Audio card=%d device=%d pcm_params=%s\n",
					m_pcm_card_idx, m_pcm_device_idx, pcm_param_string);
			int min, max;
			pcm_mask *mask = pcm_params_get_mask(m_params, PCM_PARAM_FORMAT);
			if (pcm_mask_test(mask, 0)) {
				m_audioPcmFormat = PCM_FORMAT_S8;
				m_audioFormat = (char *) "S8";
			}
			if (pcm_mask_test(mask, 2)) {
				m_audioPcmFormat = PCM_FORMAT_S16_LE;
				m_audioFormat = (char *) "S16LE";
			}
			min = pcm_params_get_min(m_params, PCM_PARAM_RATE);
			max = pcm_params_get_max(m_params, PCM_PARAM_RATE);
			if (max >= 48000)
				m_audioSamplingRate = 48000;
			else
				m_audioSamplingRate = min;
			min = pcm_params_get_min(m_params, PCM_PARAM_CHANNELS);
			max = pcm_params_get_max(m_params, PCM_PARAM_CHANNELS);
			if (max < 0 || max > 2) {
				CSIO_LOG(eLogLevel_error,
						"--UsbAudio: Audio card=%d device=%d no support for mono or stereo\n",
						m_pcm_card_idx, m_pcm_device_idx);
			} else {
			    m_audioChannels = max;
			}
			if (m_audioFormat != NULL && (m_audioChannels == 1 || m_audioChannels == 2) && m_audioSamplingRate > 0) {
				rv = true;
				CSIO_LOG(eLogLevel_error,
						"--Streamout: Audio card=%d device=%d using %s format numChannels=%d SamplingRate=%d\n",
						m_pcm_card_idx, m_pcm_device_idx, m_audioFormat, m_audioChannels,
						m_audioSamplingRate);
			} else
				CSIO_LOG(eLogLevel_error,
						"--Streamout: Audio card=%d device=%d not supported for audio\n",
						m_pcm_card_idx, m_pcm_device_idx);
        }
    }
	return rv;
}
void UsbAudio::releaseDevice()
{
    CSIO_LOG(eLogLevel_error, "--Streamout: %s", __FUNCTION__);
	if (m_params)
	{
	    CSIO_LOG(eLogLevel_error, "--Streamout: %s: freeing params", __FUNCTION__);
		pcm_params_free(m_params);
		m_params = NULL;
	}

	if (m_device)
	{
	    CSIO_LOG(eLogLevel_error, "--Streamout: %s: close pcm device", __FUNCTION__);
		pcm_close(m_device);
		m_device = NULL;
	}
}
#else

unsigned int UsbAudio::usb_audio_avail(pcm *pcm_device)
{
    return 0;
}
void UsbAudio::usb_audio_get_samples(pcm *pcm_device, void *data, int size, int channels, GstClockTime *timestamp, GstClockTime *duration) {
    return;
}
// returns true if successful
bool UsbAudio::configure()
{
	return false;
}
// returns true if successful
bool UsbAudio::getAudioParams()
{
	return false;
}
void UsbAudio::releaseDevice()
{

}
#endif   // HAS_TINYALSA

UsbAudio::UsbAudio(char *file)
{
    CSIO_LOG(eLogLevel_info, "--Streamout: usb audio device file: %s", file);
    strncpy(m_device_file, file, sizeof(m_device_file));
    if (strcmp(m_device_file, "audiotestsrc") == 0)
    {
        m_pcm_card_idx = 0;
        m_pcm_device_idx = 0;
    }
    else
    {
        if (sscanf(file, "/dev/snd/pcmC%dD%dc", &m_pcm_card_idx, &m_pcm_device_idx) != 2)
        {
            CSIO_LOG(eLogLevel_warning, "--Streamout: Invalid audio device file: %s", file);
        }
    }
    m_device = NULL;
    m_params = NULL;
    m_audioFormat = NULL;
    m_audioChannels = 0;
    m_audioSamplingRate = 0;
    m_usb_audio_sample = 0;
}

UsbAudio::~UsbAudio()
{
	releaseDevice();
}

void getAudioFormat(char *deviceFile, int *sampleRate, int *channels, char *sampleFormat, int sampleFormatLen)
{
	UsbAudio *a = new UsbAudio(deviceFile);
	if (a->getAudioParams()) {
		*sampleRate = a->getAudioSamplingRate();
		*channels = a->getAudioChannels();
		strncpy(sampleFormat, a->getAudioFormat(), sampleFormatLen);
	} else {
		*sampleRate = 0;
		*channels = 0;
		strcpy(sampleFormat, "");
	}
	delete a;
}



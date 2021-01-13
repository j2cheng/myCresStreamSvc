#ifndef __USBAUDIO_H__
#define __USBAUDIO_H__

//////////////////////////////////////////////////////////////////////////////

#include "../cresStreamOutProject.h"
#ifdef HAS_TINYALSA
#include "/opt/rk3399_android/android/external/tinyalsa/include/tinyalsa/asoundlib.h"
#else
enum pcm_format {
	PCM_FORMAT_DUMMY
};
#endif

#define USB_PCM_CARD 2
#define USB_PCM_DEVICE 0

extern int useUsbAudio;

class UsbAudio
{
public:

    UsbAudio();
    ~UsbAudio();

    bool getAudioParams();
    bool configure();
    void releaseDevice();

	int getAudioChannels() const {
		return m_audioChannels;
	}

	void setAudioChannels(int audioChannels) {
		m_audioChannels = audioChannels;
	}

	char* getAudioFormat() const {
		return m_audioFormat;
	}

	void setAudioFormat(char* audioFormat) {
		m_audioFormat = audioFormat;
	}

	enum pcm_format getAudioPcmFormat() const {
		return m_audioPcmFormat;
	}

	void setAudioPcmFormat(enum pcm_format audioPcmFormat) {
		m_audioPcmFormat = audioPcmFormat;
	}

	int getAudioSamplingRate() const {
		return m_audioSamplingRate;
	}

	void setAudioSamplingRate(int audioSamplingRate) {
		m_audioSamplingRate = audioSamplingRate;
	}

    unsigned int m_pcm_card_idx;
    unsigned int m_pcm_device_idx;
    struct pcm *m_device;
	struct pcm_params *m_params;

private:
	char *m_audioFormat;
	enum pcm_format m_audioPcmFormat;
	int m_audioSamplingRate;
	int m_audioChannels;
};

#endif //__USBAUDIO_H__


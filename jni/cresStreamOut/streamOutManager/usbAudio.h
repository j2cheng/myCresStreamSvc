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

    UsbAudio(char *file);
    ~UsbAudio();

    bool getAudioParams();
    bool configure();
    void releaseDevice();
    unsigned int usb_audio_avail(pcm *pcm_device);
    void usb_audio_get_samples(pcm *pcm_device, void *data, int size, GstClockTime *timestamp, GstClockTime *duration);
    int  pcm_mask_test(struct pcm_mask *m, unsigned int index);

    int getAudioChannels() const
    {
        return m_audioChannels;
    }

    void setAudioChannels(int audioChannels)
    {
        m_audioChannels = audioChannels;
    }

    char *getAudioFormat() const
    {
        return m_audioFormat;
    }

    void setAudioFormat(char *audioFormat)
    {
        m_audioFormat = audioFormat;
    }

    enum pcm_format getAudioPcmFormat() const
    {
        return m_audioPcmFormat;
    }

    void setAudioPcmFormat(enum pcm_format audioPcmFormat)
    {
        m_audioPcmFormat = audioPcmFormat;
    }

    int getAudioSamplingRate() const
    {
        return m_audioSamplingRate;
    }

    void setAudioSamplingRate(int audioSamplingRate)
    {
        m_audioSamplingRate = audioSamplingRate;
    }

    unsigned int m_pcm_card_idx;
    unsigned int m_pcm_device_idx;
    char m_device_file[128];
    struct pcm *m_device;
    struct pcm_params *m_params;
    guint64 m_usb_audio_sample ;

private:
    char *m_audioFormat;
    enum pcm_format m_audioPcmFormat;
    int m_audioSamplingRate;
    int m_audioChannels;
};

#endif //__USBAUDIO_H__



#include "cresStreamOutManager.h"
#include "v4l2Video.h"

#ifdef HAS_V4L2

static gboolean
print_structure_field (GQuark field_id, const GValue * value,
    gpointer user_data)
{
  gchar *val;

  if (G_VALUE_HOLDS_UINT (value)) {
    val = g_strdup_printf ("%u (0x%08x)", g_value_get_uint (value),
        g_value_get_uint (value));
  } else {
    val = gst_value_serialize (value);
  }

  if (val != NULL)
	  CSIO_LOG(eLogLevel_info, "\n\t\t%s = %s", g_quark_to_string (field_id), val);
  else
	  CSIO_LOG(eLogLevel_info, "\n\t\t%s - could not serialise field of type %s",
        g_quark_to_string (field_id), G_VALUE_TYPE_NAME (value));

  g_free (val);

  return TRUE;
}

static void
update_max_frame_rate(const GValue *fr, int *max_fr_num, int *max_fr_den, double *max_frame_rate)
{
    if (G_VALUE_TYPE(fr) == GST_TYPE_FRACTION)
    {
        int fr_num = gst_value_get_fraction_numerator(fr);
        int fr_den = gst_value_get_fraction_denominator(fr);
        double frd = (double) fr_num/(double) fr_den;
        if (frd > *max_frame_rate) {
            *max_frame_rate = frd;
            *max_fr_num = fr_num;
            *max_fr_den = fr_den;
        }
    }
}

static bool isFormat(const char *format, const char *fourcc)
{
	return (!strcmp(format, fourcc));
}

static int isFormatRank(const char *fourcc)
{
	char *formats[]={"NV21", "UYVY", "YUY2", "I420", "NV12", NULL};
	if (fourcc == NULL || strlen(fourcc) != 4)
		return 0;
	for (int i=0; formats[i]; i++) {
		if (strcmp(fourcc, formats[i]) == 0)
			return i+1;
	}
	return 0;
}

static void
get_video_caps_from_caps(GstCaps *caps, int min_frame_rate, VideoCaps *video_caps)
{
    int maxw = 0, maxh = 0;
    int max_frmrate_num = 0;
    int max_frmrate_den = 0;
    char fmt[5]={0};
    const gchar *format = NULL;

    guint capslen = gst_caps_get_size(caps);
    for (guint idx=0; idx < capslen; idx++)
    {
        GstStructure *s = gst_caps_get_structure(caps, idx);
        if (!s) {
        	CSIO_LOG(eLogLevel_error, "Could not get structure for index=%d\n", idx);
        } else {
            const gchar *sname = gst_structure_get_name(s);
            if (strcasecmp(sname, "video/x-raw") == 0)
            {
                #if 0
                gchar *ss = gst_structure_to_string(s);
                CSIO_LOG(eLogLevel_info, "\tStructure %d = %s\n", idx, ss);
                g_free(ss);
                #endif

                gint width = 0;
                gint height = 0;
                const GValue *aspect_ratio;
                gint ar_num = 1, ar_den = 1;
                const GValue *frame_rate;
                double max_frame_rate=0;
                int max_fr_num = 1;
                int max_fr_den = 1;

                if (gst_structure_has_field(s, "framerate"))
                    frame_rate = gst_structure_get_value(s, "framerate");
                if (G_VALUE_TYPE(frame_rate) == GST_TYPE_FRACTION)
                {
                    update_max_frame_rate(frame_rate, &max_fr_num, &max_fr_den, &max_frame_rate);
                } else if (G_VALUE_TYPE(frame_rate) == GST_TYPE_LIST) {
                    int size = gst_value_list_get_size(frame_rate);
                    for (int i=0; i < size; i++)
                    {
                        const GValue *fr = gst_value_list_get_value(frame_rate, i);
                        update_max_frame_rate(fr, &max_fr_num, &max_fr_den, &max_frame_rate);
                    }
                }

                // min frame rate requirement satisfied - select highest resolution
                if (gst_structure_has_field(s, "format"))
                    format = gst_structure_get_string(s, "format");
                if (!isFormat(format,"YUY2") && !isFormat(format,"NV12") && !isFormat(format, "NV21"))
                    continue;
                if (gst_structure_has_field(s, "width"))
                    gst_structure_get_int(s, "width", &width);
                if (gst_structure_has_field(s, "height"))
                    gst_structure_get_int(s, "height", &height);
                if (gst_structure_has_field(s, "pixel-aspect-ratio"))
                    aspect_ratio = gst_structure_get_value(s, "pixel-aspect-ratio");
                if (G_VALUE_TYPE(aspect_ratio) == GST_TYPE_FRACTION)
                {
                    ar_num = gst_value_get_fraction_numerator(aspect_ratio);
                    ar_den = gst_value_get_fraction_denominator(aspect_ratio);
                }

                CSIO_LOG(eLogLevel_info, "\tformat=%s width=%u height=%u framerate=%6.3f aspect-ratio=%d/%d framerates=%s\n",
                    format, width, height, max_frame_rate, ar_num, ar_den, gst_value_serialize(frame_rate));

                if (max_frame_rate < ((double) min_frame_rate)) {
                    //CSIO_LOG(eLogLevel_info, "ignoring because max_frame_rate is %f and framerate threashold=%d\n", max_frame_rate, min_frame_rate);
                    continue;
                }

                if (((maxw)*(maxh) < width*height) || (isFormatRank(fmt) < isFormatRank(format)))
                {
                    strcpy(fmt, format);
                    maxw = width;
                    maxh = height;
                    max_frmrate_num = max_fr_num;
                    max_frmrate_den = max_fr_den;
                    CSIO_LOG(eLogLevel_info, "best so far: fmt=%s w=%d h=%d frnum=%d frden=%d\n", format, maxw, maxh, max_frmrate_num,max_frmrate_den);
                }

            }
        }
    }

    if (maxw > 0)
    {
        strcpy(video_caps->format, fmt);
        video_caps->w = maxw;
        video_caps->h = maxh;
        video_caps->frame_rate_num = max_frmrate_num;
        video_caps->frame_rate_den = max_frmrate_den;
    }
}

int get_video_caps(char *device_name, VideoCaps *video_caps, char *display_name, int display_name_len)
{
    video_caps->w = 0;
    video_caps->h = 0;
    video_caps->frame_rate_num = 1;
    video_caps->frame_rate_den = 1;

    GstDeviceMonitor *monitor = gst_device_monitor_new();
    gst_device_monitor_add_filter(monitor, "Video/Source", NULL);
    if (!gst_device_monitor_start(monitor))
    {
        CSIO_LOG(eLogLevel_error, "Unable to start device monitor\n");
        return -1;
    }

    GList *devices = gst_device_monitor_get_devices(monitor);
    GList *elem;
    for (elem=devices; elem != NULL; elem=elem->next)
    {
        GstDevice *device = (GstDevice *) elem->data;
        gchar *devclass = gst_device_get_device_class(device);
        if (strcasecmp(devclass,"Video/Source") == 0)
        {
            const gchar *devname=NULL;;
            GstStructure *props;
            props = gst_device_get_properties(device);
            if (props) {
                devname = gst_structure_get_string(props, "device.path");
                if (devname)
                {
                	//CSIO_LOG(eLogLevel_info, "\tDevice name=%s\n", devname);
                }
                //CSIO_LOG(eLogLevel_info, "\tproperties:");
                //gst_structure_foreach (props, print_structure_field, NULL);
                //CSIO_LOG(eLogLevel_info, "\n");
            }
            gchar *devdisplayname = gst_device_get_display_name(device);
            CSIO_LOG(eLogLevel_info, "Got device %s (display_name=%s) of class %s \n", devname, devdisplayname, devclass);
            strncpy(display_name, devdisplayname, sizeof(display_name));
            g_free(devdisplayname);
            if ((device_name == NULL) || (strcmp(devname, device_name) == 0))
            {
                GstCaps *caps = gst_device_get_caps(device);
                if (!caps) {
                	CSIO_LOG(eLogLevel_error, "Could not get caps for device\n");
                } else {
                    get_video_caps_from_caps(caps, 15, video_caps);
                }
                gst_caps_unref(caps);
                CSIO_LOG(eLogLevel_info, "Selected caps: format=%s w=%d h=%d framerate=%d/%d \n", video_caps->format,
                    video_caps->w, video_caps->h, video_caps->frame_rate_num, video_caps->frame_rate_den);
            }
            gst_structure_free (props);
         }

        g_free(devclass);
    }

    gst_device_monitor_stop(monitor);
    g_object_unref(monitor);
    return 0;
}

int get_video_caps_string(VideoCaps *video_caps, char *caps, int maxlen)
{
	int n = snprintf(caps, maxlen, "video/x-raw,format=%s,width=%d,height=%d,framerate=%d/%d",
			video_caps->format, video_caps->w, video_caps->h, video_caps->frame_rate_num, video_caps->frame_rate_den);
	if (n > 0 && n < maxlen)
		return 0;
	else
		return -1;
}
#else    // HAS_V4L2
int get_video_caps(char *device_name, VideoCaps *video_caps, char *display_name, int display_name_len)
{
    video_caps->w = 0;
    video_caps->h = 0;
    video_caps->frame_rate_num = 1;
    video_caps->frame_rate_den = 1;
    strncpy(display_name, "Unknown Device", display_name_len);
}
int get_video_caps_string(VideoCaps *video_caps, char *caps, int maxlen)
{
	return -1;
}
#endif   // HAS_V4L2






/*
  Test Stub: Crestron Emulator To Test Android Service 

 */
#include<stdio.h>   //printf
#include<string.h> //memset
#include<stdlib.h> //exit(0);
#include<arpa/inet.h>
#include<sys/socket.h>
#include<ctype.h>

#define BUFLEN 512  //Max length of buffer
#define PORT 9876   //The port on which to send data

void die(char *s)
{
	perror(s);
	exit(1);
}

int main(int argc, char *argv[])
{
	struct sockaddr_in si_other;
	int s, s2, i, slen=sizeof(si_other);
	char buf[BUFLEN];
	char message[BUFLEN];

	if(argc==1) {
		fprintf(stderr,"please provide server ip to send data\n");
		exit(-1);
	}
	if ( (s=socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)) == -1)        // create a client socket
	{
		die("socket");
	}

	FILE *file;
	char *line = NULL;
	size_t len = 0;
	char read;
	file=fopen("./test-config", "r");
	if (file == NULL)
	    return 1;

	memset((char *) &si_other, 0, sizeof(si_other));
	si_other.sin_family = AF_INET;
	si_other.sin_port = htons(PORT);

	if (inet_aton(argv[1] , &si_other.sin_addr) == 0)            // Create datagram with server IP and port.
	{
		fprintf(stderr, "inet_aton() failed\n");
		exit(1);
	}

		fprintf(stderr, "Enter message : \033[22;31m format<Tx;TS;192.168.50.112;1234;1920;1080>\n\
		\033[22;33m SessInitMode   : Pre\t Rx\t Tx\t McastRTSP\t McastUdp\t\n \
		\033[22;32m TransportMode  : TS \t RTSP\t RTP UDP\n \
		\033[22;35m IpAddrAndPort\t: IPAddress and Port for streamout/ Url to streamin\n \
		\033[22;36m OutResolution \t: any standard resolution like 1920;1080 etc \n \
		\033[22;38m EncProfiles   \t: BP \t MP \t HP \t \n \
		\033[22;34m Play Ctrls\t: start\t stop\t pause\n \
		\033[01;37m");
	//while(1)
	while ((read = getline(&line, &len, file)) != -1) 
	{
		//gets(message);
		for(i=0; i<len; i++)
		{
			if(line[i] == '\n')
			{
				// Move all the char following the char "c" by one to the left.
				strncpy(&line[i],&line[i+1],len-i);
			}
		}
		fprintf(stderr, "%s-", line);
		if (sendto(s, line, strlen(line) , 0 , (struct sockaddr *) &si_other, slen)==-1)
		{
			die("sendto()");
		}


		memset(buf,'\0', BUFLEN);

		puts(buf);
		sleep(20);
	}

	close(s);
	return 0;
}


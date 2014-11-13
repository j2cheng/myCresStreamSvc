/* 
 * TcpCresEmulator-  To Test Mistral Functionalities
 * usage: TcpCresEmulator- <evs_device IP> 
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h> 

#define BUFSIZE 1024
#define PORT_NO 9876

void error(char *msg) {
    perror(msg);
    exit(0);
}

int main(int argc, char **argv) {
	int sockfd, portno, n;
	struct sockaddr_in serveraddr;
	struct hostent *server;
	char *hostname;
	char buf[BUFSIZE];

	/* check command line arguments */
	if (argc != 2) {
		fprintf(stderr,"usage: %s <hostname> \n", argv[0]);
		exit(0);
	}
	hostname = argv[1];

	/* socket: create the socket */
	sockfd = socket(AF_INET, SOCK_STREAM, 0);
	if (sockfd < 0) 
		error("ERROR opening socket");

	/* gethostbyname: get the server's DNS entry */
	server = gethostbyname(hostname);
	if (server == NULL) {
		fprintf(stderr,"ERROR, no such host as %s\n", hostname);
		exit(0);
	}

	/* build the server's Internet address */
	bzero((char *) &serveraddr, sizeof(serveraddr));
	serveraddr.sin_family = AF_INET;
	bcopy((char *)server->h_addr, 
			(char *)&serveraddr.sin_addr.s_addr, server->h_length);
	serveraddr.sin_port = htons(PORT_NO);

	/* connect: create a connection with the server */
	if (connect(sockfd, (const struct sockaddr*) &serveraddr, sizeof(serveraddr)) < 0) 
		error("ERROR connecting");
	while(1){
		/* get message line from the user */
		printf("TxRx CMD:");
		bzero(buf, BUFSIZE);
		fgets(buf, BUFSIZE, stdin);

		/* send the message line to the server */
		n = write(sockfd, buf, strlen(buf));
		if (n < 0) 
			error("ERROR writing to socket");

		/* print the server's reply */
		bzero(buf, BUFSIZE);
		n = read(sockfd, buf, BUFSIZE);
		if (n < 0) 
			error("ERROR reading from socket");
		printf("TxRX CMD: ServerResponse :%s\n", buf);
	}
	close(sockfd);
	return 0;
}

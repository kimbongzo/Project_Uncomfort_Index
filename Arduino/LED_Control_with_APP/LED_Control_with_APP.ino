#include <Arduino.h>
#include <SPI.h>
#include <IPAddress.h>
#include "WizFi250.h"
#include "DHT11.h"

char ssid[] = "wizms1";    // your network SSID (name)
char pass[] = "maker0701";          // your network password
int status = WL_IDLE_STATUS;       // the Wifi radio's status

// Hardware Pin status
#define Pin_DHT         6
#define Pin_CDS         A5
#define Pin_LED         8

// Server Information
#define LOCAL_PORT 5000
WiFiServer server(LOCAL_PORT);
WiFiClient client;
DHT11 dht11(Pin_DHT);

IPAddress ip(192, 168, 1, 6);
IPAddress sn(255, 255, 255, 0);
IPAddress gw(192, 168, 1, 1);

struct msgFormat{
  byte start = 0x88;
  byte type = 0x00;
  byte dest = 0x00;
  byte source = 0x00;
  byte status = 0x00;
  byte sensor1[2] = {0x00, 0x00};
  byte sensor2[2] = {0x00, 0x00};
  byte sensor3 = 0x00;
  byte end = 0x55;
};

msgFormat SendMsg;
// use a ring buffer to increase speed and reduce memory allocation
//WizFiRingBuffer buf(50);
bool isLEDOn = false;
byte recvMsg[11];
int msgPtr = 0;
bool msgFlag = false;

unsigned long lastConnectionTime = 0;         // last time you connected to the server, in milliseconds
const unsigned long postingInterval = 2000L; // delay between updates, in milliseconds

void sendMessage();
void printWifiStatus();

void setup()
{
  SerialUSB.begin(9600);
  SerialUSB.println("\r\nSerial Init");

  pinMode(Pin_LED, OUTPUT);   //added
  digitalWrite(Pin_LED, LOW);

  WiFi.init();

  // check for the presence of the shield
  if (WiFi.status() == WL_NO_SHIELD) {
    SerialUSB.println("WiFi shield not present");
    // don't continue
    while (true);
  }

  // attempt to connect to WiFi network
  while ( status != WL_CONNECTED) {
    SerialUSB.print("Attempting to connect to WPA SSID: ");
    SerialUSB.println(ssid);
    // Connect to WPA/WPA2 network
   // WiFi.config(ip, sn, gw);
    status = WiFi.begin(ssid, pass);
  }

  // you're connected now, so print out the data
  SerialUSB.println("You're connected to the network");

  printWifiStatus();
  
  
}

void loop()
{
  char c;
  
  // listen for incoming clients
   client = server.available();
             
  if( client ) {
    SerialUSB.println("New client");
    lastConnectionTime = millis();
    // an http request ends with a blank line
  
    while (client.connected()) {
        if (client.available()) {
          c = client.read();
//          SerialUSB.write(c);
          if( c == (char)0x88) {
              msgPtr = 0;
              recvMsg[msgPtr++] = c;
          } else if (c != -1){
              recvMsg[msgPtr++] = c;
          }

        }
      
       if( recvMsg[0]==(char)0x88 && recvMsg[10]==(char)0x55 ) {
          msgFormat* tmp = (msgFormat*) recvMsg;
        
          SendMsg.status = tmp->status;
          
          if( SendMsg.status == 0x01 ) {
              digitalWrite(Pin_LED, HIGH);
          //    SerialUSB.println("O N");
          } else if( SendMsg.status == 0x00 ) {
              digitalWrite(Pin_LED, LOW);
        //      SerialUSB.println("OFF");
          }
        }
      
       if ( millis() - lastConnectionTime > postingInterval ) {
         
            sendMessage();
       }
      
      
    }
    // give the web browser time to receive the data
    delay(10);

    // close the connection:
    client.stop();
    SerialUSB.println("Client disconnected");
  }
}


void sendMessage(){
  
  int err;
  float temp, humi;
  unsigned int illumi;
  
  illumi = analogRead(Pin_CDS);
  SendMsg.sensor1[0] = ((int)illumi >> 8) & 0xFF;
  SendMsg.sensor1[1] =  ((int)illumi & 0xFF);
  
  if(dht11.read(humi, temp)==0) {
       SendMsg.sensor2[0] = ((int)humi >> 8) & 0xFF;
       SendMsg.sensor2[1] =  ((int)humi & 0xFF);
       SendMsg.sensor3 = ((int)temp & 0xFF);
  }
  
  
  char msg[sizeof(SendMsg)];
  memcpy(msg, &SendMsg, sizeof(SendMsg));
  
 
  if( SendMsg.status == 0x01 ) {
      digitalWrite(Pin_LED, HIGH);
      SerialUSB.println("O N");
  } else if( SendMsg.status == 0x00 ) {
      digitalWrite(Pin_LED, LOW);
      SerialUSB.println("OFF");
  }
  client.write(msg,sizeof(msg));
  
  lastConnectionTime = millis();

}

void printWifiStatus()
{
  // print the SSID of the network you're attached to
  SerialUSB.print("SSID: ");
  SerialUSB.println(WiFi.SSID());

  // print your WiFi shield's IP address
  IPAddress ip = WiFi.localIP();
  SerialUSB.print("IP Address: ");
  SerialUSB.println(ip);

  // print the received signal strength
  long rssi = WiFi.RSSI();
  SerialUSB.print("Signal strength (RSSI):");
  SerialUSB.print(rssi);
  SerialUSB.println(" dBm");
}



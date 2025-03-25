#include <SoftwareSerial.h>


//5V , GND , TXD: 8 , RXD:9
SoftwareSerial mySerial(8, 9); 

void setup() {

  Serial.begin(9600);
  mySerial.begin(9600);

}

void loop() {

  if (mySerial.available()) {
    Serial.write(mySerial.read());
  }
  if (Serial.available()) {
    mySerial.write(Serial.read());
  }
}

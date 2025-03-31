#include <SoftwareSerial.h>


//5V , GND , TXD: 8 , RXD:9
//디지털 핀 8을 RXD(수신)로, 핀 9를 TXD(송신)로 사용하는 소프트웨어 시리얼 포트를 생성
SoftwareSerial mySerial(8, 9); 

void setup() {

  Serial.begin(9600);
  mySerial.begin(9600);

}

void loop() {


// available()는 기본 시리얼 포트로 들어온 데이터의 수신 여부를 확인합니다.
// 수신된 데이터가 있으면, read()를 통해 데이터를 읽어오고, 
// write()를 통해 소프트웨어 시리얼 포트(블루투스 모듈과 연결된 포트)로 데이터를 전송합니다.


  if (mySerial.available()) { //아두이노 보드에 꽂힌 블루투스 모듈과 연결된 외부 기긱
    Serial.write(mySerial.read()); 
  }
  if (Serial.available()) { // 아두이노 보드와 PC
    mySerial.write(Serial.read());
  }
}

/*-----
이 아두이노 프로그램은 Serial과 mySerial 두 개의 시리얼 통신 객체를 사용하여 두 개의 시리얼 장치 간에 데이터를 송수신합니다. 각 객체의 역할을 설명하겠습니다.

Serial
Serial은 아두이노 보드의 기본 시리얼 통신 포트를 나타냅니다. 일반적으로 USB를 통해 컴퓨터와 통신할 때 사용됩니다.
Serial.begin(9600)은 보드와 컴퓨터 간의 통신 속도를 9600 bps로 설정합니다.
Serial.available()는 보드와 컴퓨터 간에 읽을 수 있는 데이터가 있는지 확인합니다.
Serial.read()는 보드와 컴퓨터 간에 들어온 데이터를 읽습니다.
Serial.write()는 보드와 컴퓨터 간에 데이터를 전송합니다.

mySerial
mySerial은 SoftwareSerial 라이브러리를 사용하여 생성된 소프트웨어 시리얼 포트입니다. 
이 포트는 하드웨어적으로 지원되지 않는 추가 시리얼 포트를 제공합니다.(ex, 블루투스)
SoftwareSerial mySerial(8, 9)는 디지털 핀 8을 RXD(수신)로, 핀 9를 TXD(송신)로 사용하는 소프트웨어 시리얼 포트를 생성합니다.
mySerial.begin(9600)은 소프트웨어 시리얼 포트와의 통신 속도를 9600 bps로 설정합니다.
mySerial.available()는 소프트웨어 시리얼 포트에 읽을 수 있는 데이터가 있는지 확인합니다.
mySerial.read()는 소프트웨어 시리얼 포트로 들어온 데이터를 읽습니다.
mySerial.write()는 소프트웨어 시리얼 포트로 데이터를 전송합니다.

프로그램 동작 설명
setup() 함수에서 두 개의 시리얼 포트를 모두 9600 bps로 초기화합니다.
loop() 함수에서 다음을 수행합니다:
mySerial 포트에 데이터가 들어오면, 이를 읽어서 Serial 포트로 전송합니다.
Serial 포트에 데이터가 들어오면, 이를 읽어서 mySerial 포트로 전송합니다.
결론적으로, 이 프로그램은 두 개의 시리얼 포트 간에 데이터를 중계하는 역할을 합니다. Serial은 기본 시리얼 통신(예: USB)을, mySerial은 추가적인 소프트웨어 시리얼 통신을 처리하는 데 사용됩니다.

---*/
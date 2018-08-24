import processing.io.*;
OPC opc;

int r = 0;
int g = 0;
int b = 0;
int x = 0;
boolean o = false;
int lastTime = 0;
int debounce = 200;
  
void setup()
{
  size(270,200);
  frameRate(10);
  colorMode(RGB, 100);
  opc = new OPC(this, "127.0.0.1", 7890);

  /*opc.ledStrip(0, 18, 230, 30, 2, 0, false); //why when 2.5 spacing, coupled?
  opc.ledStrip(18, 27, 50, 80, 2, 0, true);
  opc.ledStrip(45, 36, 140, 80, 2, 0, true);
  opc.ledStrip(81, 27, 230, 80, 2, 0, false);
  opc.ledStrip(107, 36, 50, 130, 2, 0, true);
  opc.ledStrip(143, 36, 140, 130, 2, 0, true);
  opc.ledStrip(179, 36, 230, 130, 2, 0, false);
  opc.ledStrip(215, 64, 180, 180, 2, 0, true);
  */
  
  GPIO.pinMode(5, GPIO.INPUT); //red
  GPIO.pinMode(6, GPIO.INPUT); //green
  GPIO.pinMode(4, GPIO.INPUT); //blue
  GPIO.pinMode(17, GPIO.INPUT); //fancy
  GPIO.pinMode(27, GPIO.INPUT); //on/off
  
  for (int i = 0; i<279; i++){
    opc.setPixel(i, color(0, 0, 0));
  }
  opc.writePixels();
}

void draw()
{
  //background(r, g, b);
  if ((millis() - lastTime) > debounce){
    if (GPIO.digitalRead(27) == GPIO.HIGH){ //simple on/off button
      o = !o;
      r = 0;
      g = 0;
      b = 0;
      x = 0;
      colorMode(RGB, 100);
      if (o){
        for (int i = 0; i<279; i++){
          opc.setPixel(i, color(100, 100, 100));
        }
      }
      else{
        for (int i = 0; i<279; i++){
          opc.setPixel(i, color(0, 0, 0));
        }
      }
      lastTime = millis();
    }
    else if (GPIO.digitalRead(5) == GPIO.HIGH){ //red button
    colorMode(RGB, 100);
      if (r > 100){
        r = 0;
      }
      else {
        r += 20;
      }
      for (int i = 0; i<279; i++){
          opc.setPixel(i, color(r, g, b));
        }
      lastTime = millis(); 
    }
    else if (GPIO.digitalRead(6) == GPIO.HIGH){ //green button
    colorMode(RGB, 100);
      if (g > 100){
        g = 0;
      }
      else {
        g += 20;
      }
      for (int i = 0; i<279; i++){
          opc.setPixel(i, color(r, g, b));
        }
      lastTime = millis(); 
    }
    else if (GPIO.digitalRead(4) == GPIO.HIGH){ //blue button
    colorMode(RGB, 100);
      if (b > 100){
        b = 0;
      }
      else {
        b += 20;
      }
      for (int i = 0; i<279; i++){
          opc.setPixel(i, color(r, g, b));
        }
      lastTime = millis(); 
    }
  else if (GPIO.digitalRead(17) == GPIO.HIGH){ //fancy button cycle
      x += 1;
      if (x > 2){
        x = 0;
      }
    lastTime = millis();
  }
  else {
    if (x == 1) {
      colorMode(HSB, 100);
      for (int i = 0; i<64; i++){
        float hue = (millis() * 0.01 + i * 2.0) % 100;
        for (int j = 0; j<279; j++){
          opc.setPixel(j, color(hue, 20, 80));
        } 
      }
      //opc.writePixels();
     }
    else if (x == 2) {
      colorMode(HSB, 100);
      for (int i = 0; i<64; i++){
        float hue = (millis() * 0.01 + i * 2.0) % 100;
        for (int j = 0; j<279; j++){
          opc.setPixel(j, color(hue, 80, 100));
        }  
      }
      //opc.writePixels();
    }
    lastTime = millis(); 
  }
  opc.writePixels();
  }
}

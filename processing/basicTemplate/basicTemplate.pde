// This is an empty Processing sketch with support for Fadecandy.

OPC opc;

void setup()
{
  size(270,200);
  opc = new OPC(this, "127.0.0.1", 7890);

  opc.ledStrip(0, 18, 230, 30, 2, 0, false); //why when 2.5 spacing, coupled?
  opc.ledStrip(18, 27, 50, 80, 2, 0, true);
  opc.ledStrip(45, 36, 140, 80, 2, 0, true);
  opc.ledStrip(81, 27, 230, 80, 2, 0, false);
  opc.ledStrip(107, 36, 50, 130, 2, 0, true);
  opc.ledStrip(143, 36, 140, 130, 2, 0, true);
  opc.ledStrip(179, 36, 230, 130, 2, 0, false);
  opc.ledStrip(215, 64, 180, 180, 2, 0, true);
}

void draw()
{
  background(255, 0, 0);

  // Draw each frame here
}

[out:json][timeout:60];
(
  node["highway"="speed_camera"](36.6,12.5,41.4,18.5);
  node["man_made"="speed_camera"](36.6,12.5,41.4,18.5);
  node["enforcement"="maxspeed"](36.6,12.5,41.4,18.5);
  node["enforcement"="average_speed"](36.6,12.5,41.4,18.5);
  node["camera:type"="speed"](36.6,12.5,41.4,18.5);
  node["camera:type"="red_light"](36.6,12.5,41.4,18.5);
  node["highway"="traffic_signals"]["camera"="yes"](36.6,12.5,41.4,18.5);
);
out ids;

[out:json][timeout:60];
(
  node["highway"="speed_camera"](41.4,12.5,47.1,18.5);
  node["man_made"="speed_camera"](41.4,12.5,47.1,18.5);
  node["enforcement"="maxspeed"](41.4,12.5,47.1,18.5);
  node["enforcement"="average_speed"](41.4,12.5,47.1,18.5);
  node["camera:type"="speed"](41.4,12.5,47.1,18.5);
  node["camera:type"="red_light"](41.4,12.5,47.1,18.5);
  node["highway"="traffic_signals"]["camera"="yes"](41.4,12.5,47.1,18.5);
);
out ids;

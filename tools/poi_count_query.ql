[out:json][timeout:120];
area["ISO3166-1"="IT"][admin_level=2]->.it;
(
  node["highway"="speed_camera"](area.it);
  node["man_made"="speed_camera"](area.it);
  node["enforcement"="maxspeed"](area.it);
  node["enforcement"="average_speed"](area.it);
  node["camera:type"="speed"](area.it);
  node["camera:type"="red_light"](area.it);
);
out ids;

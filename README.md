WOLforwarder
============

an android service to forward a valid WOL (wake on lan) packet from a specific port in the local network to a broadcast on the local network.
this is useful for routers that do not allow a port forwarding rule to a broadcast IP. using this code on an android device on the local network, the device can be used to listen on a specified port and forward WOL packets on that port using a broadcast to reach the sleeping pc.
this way, instead of using port forwarding to a broadcast IP which can't be done on the router, you instead port forward to the android device, and let it do the broadcast.


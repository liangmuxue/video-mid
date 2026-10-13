package com.jizhi.videomid.gb28181.sip.live;

import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.RouteHeader;
import javax.sip.message.Request;
import java.text.ParseException;
import java.util.ListIterator;
import java.util.Locale;

/** 把 ACK / BYE 的下一跳改到报文实际来源，避免按 Contact 里的内网地址发送。 */
final class SipSignalTarget {

    private SipSignalTarget() {
    }

    static void retarget(Address target, String host, int port, String transport) throws ParseException {
        if (target != null && target.getURI() instanceof SipURI uri) {
            apply(uri, host, port, transport);
        }
    }

    static void retarget(Request request, String host, int port, String transport) throws ParseException {
        if (request.getRequestURI() instanceof SipURI uri) {
            apply(uri, host, port, transport);
        }
        ListIterator<?> routes = request.getHeaders(RouteHeader.NAME);
        if (routes == null) {
            return;
        }
        while (routes.hasNext()) {
            Object header = routes.next();
            if (header instanceof RouteHeader route
                    && route.getAddress() != null
                    && route.getAddress().getURI() instanceof SipURI uri) {
                apply(uri, host, port, transport);
            }
        }
    }

    private static void apply(SipURI uri, String host, int port, String transport) throws ParseException {
        uri.setHost(host);
        uri.setPort(port);
        if (transport != null && !transport.isBlank()) {
            uri.setTransportParam(transport.trim().toLowerCase(Locale.ROOT));
        }
    }
}

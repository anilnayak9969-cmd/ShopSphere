package com.example.ecommerce.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.services.LiveEventService;

import lombok.RequiredArgsConstructor;

/**
 * Live event stream for the signed-in user (Server-Sent Events). The frontend opens it with fetch()
 * so the JWT travels in the Authorization header, never in the URL.
 * Events: "notification" (a new bell notification, with its link), "pickups" (delivery partners:
 * the list of parcels ready for pickup changed).
 */
@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
public class LiveController {

    private final LiveEventService liveEventService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal UserPrincipal user) {
        return liveEventService.subscribe(user.getId(), user.getRole());
    }
}

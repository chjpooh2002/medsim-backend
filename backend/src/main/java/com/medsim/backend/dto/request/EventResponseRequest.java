package com.medsim.backend.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventResponseRequest {
    private String eventId;
    private String optionId;
}

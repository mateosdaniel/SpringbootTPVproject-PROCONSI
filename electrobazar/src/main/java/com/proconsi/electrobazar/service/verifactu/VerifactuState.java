package com.proconsi.electrobazar.service.verifactu;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Getter
@Setter
public class VerifactuState {
    private volatile LocalDateTime lastSendTime = null;
    private volatile int currentWaitSeconds = 60;

    public boolean isCooldownActive() {
        if (lastSendTime == null) return false;
        return LocalDateTime.now().isBefore(lastSendTime.plusSeconds(currentWaitSeconds));
    }

    public long getRemainingSeconds() {
        if (lastSendTime == null) return 0;
        long elapsed = java.time.Duration.between(lastSendTime, LocalDateTime.now()).toSeconds();
        long remaining = currentWaitSeconds - elapsed;
        return Math.max(0, remaining);
    }
}

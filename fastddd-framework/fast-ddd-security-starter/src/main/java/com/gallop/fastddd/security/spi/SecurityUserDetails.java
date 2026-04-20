package com.gallop.fastddd.security.spi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityUserDetails {

    private Long userId;
    private String username;
    private String password;
    private boolean enabled;
}

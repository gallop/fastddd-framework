package com.gallop.fastddd.security.dto;

import com.gallop.fastddd.security.spi.SecurityUserInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {

    private SecurityUserInfo user;
    private List<String> permissions;
    private List<String> roles;
}

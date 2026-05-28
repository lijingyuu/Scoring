package com.scoring.backend.service;

import com.scoring.backend.domain.dto.UpdateProfileReq;
import com.scoring.backend.domain.vo.UserProfileVO;

public interface UserService {

    UserProfileVO getCurrentProfile(String userId);

    UserProfileVO updateProfile(String userId, UpdateProfileReq req);
}

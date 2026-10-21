package com.james.LMS.admin.dto.request;

import com.james.LMS.enums.RoleEnum;
import java.util.List;

public record UpdateUserRequest(String username, List<RoleEnum> roles, boolean active) {}

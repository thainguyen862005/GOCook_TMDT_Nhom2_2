package com.james.LMS.controller;

import com.james.LMS.facade.UserFacadeV2;
import com.james.LMS.service.AuthChallengeService;
import jakarta.servlet.http.HttpServletRequest;
import com.james.LMS.request.LoginRequest;
import com.james.LMS.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v2/users")
@RequiredArgsConstructor
public class UserControllerV2 {

  private final UserFacadeV2 userFacadeV2;
  private final AuthChallengeService challenges;

  @PostMapping("/login")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
      summary = "Login to system by email and password",
      tags = {"User v2 APIs update to test deploy production"})
  public BaseResponse<Void> login(
      @Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
    challenges.requireIpRate("login", request.getRemoteAddr(), 200, 15);
    return this.userFacadeV2.login(loginRequest, response);
  }
}

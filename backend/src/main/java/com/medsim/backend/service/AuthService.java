package com.medsim.backend.service;

import com.medsim.backend.domain.Member;
import com.medsim.backend.dto.request.LoginRequest;
import com.medsim.backend.dto.request.SignupRequest;
import com.medsim.backend.dto.response.AuthResponse;
import com.medsim.backend.repository.MemberRepository;
import com.medsim.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse signup(SignupRequest req) {
        if (memberRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        Member member = Member.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .name(req.getName())
                .role(Member.Role.USER)
                .build();

        memberRepository.save(member);
        return new AuthResponse(jwtUtil.generateToken(member.getEmail()), member.getEmail(), member.getName());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        Member member = memberRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(req.getPassword(), member.getPassword())) {
            throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        return new AuthResponse(jwtUtil.generateToken(member.getEmail()), member.getEmail(), member.getName());
    }
}

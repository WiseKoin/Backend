package com.wisekoin.backend.module.goal;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.goal.dto.GoalFundRequest;
import com.wisekoin.backend.module.goal.dto.GoalRequest;
import com.wisekoin.backend.module.goal.dto.GoalResponse;
import com.wisekoin.backend.module.goal.mapper.GoalMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
@Slf4j
public class GoalController {

    private final GoalService goalService;
    private final GoalMapper goalMapper;

    @PostMapping
    public ResponseEntity<GoalResponse> createGoal(
            @Valid @RequestBody GoalRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        Goal goal = goalService.createGoal(currentUser.getId(), request);
        return ResponseEntity.ok(goalMapper.toResponse(goal));
    }

    @GetMapping
    public ResponseEntity<List<GoalResponse>> getGoals(
            @RequestParam(required = false) Goal.GoalStatus status,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        List<Goal> goals = goalService.getGoals(currentUser.getId(), status);
        List<GoalResponse> responses = goals.stream()
                .map(goalMapper::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{id}/fund")
    public ResponseEntity<GoalResponse> fundGoal(
            @PathVariable Long id,
            @Valid @RequestBody GoalFundRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        Goal goal = goalService.fund(currentUser.getId(), id, request);
        return ResponseEntity.ok(goalMapper.toResponse(goal));
    }

}

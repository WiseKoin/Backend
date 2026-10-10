package com.wisekoin.backend.module.goal;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.goal.dto.GoalFundRequest;
import com.wisekoin.backend.module.goal.dto.GoalRequest;
import com.wisekoin.backend.module.goal.dto.GoalResponse;
import com.wisekoin.backend.module.goal.dto.GoalUpdateRequest;
import com.wisekoin.backend.module.goal.mapper.GoalMapper;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Goal goal = goalService.createGoal(currentUser.getId(), request);
        return ResponseEntity.ok(goalMapper.toResponse(goal));
    }

    @GetMapping
    public ResponseEntity<List<GoalResponse>> getGoals(
            @RequestParam(required = false) Goal.GoalStatus status,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        List<Goal> goals = goalService.getGoals(currentUser.getId(), status);
        List<GoalResponse> responses = goals.stream().map(goalMapper::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{id}/fund")
    public ResponseEntity<GoalResponse> fundGoal(
            @PathVariable Long id,
            @Valid @RequestBody GoalFundRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Goal goal = goalService.fund(currentUser.getId(), id, request);
        return ResponseEntity.ok(goalMapper.toResponse(goal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> updateGoal(
            @PathVariable Long id,
            @Valid @RequestBody GoalRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                goalMapper.toResponse(goalService.updateGoal(currentUser.getId(), id, request)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<GoalResponse> patchGoal(
            @PathVariable Long id,
            @Valid @RequestBody GoalUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                goalMapper.toResponse(goalService.patchGoal(currentUser.getId(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoal(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails currentUser) {
        goalService.deleteGoal(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}

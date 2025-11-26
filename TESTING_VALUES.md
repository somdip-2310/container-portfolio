# Testing Values Configuration

## Current Testing Values (for quick testing)

| Setting | Production Value | Testing Value | File Location |
|---------|-----------------|---------------|---------------|
| FREE plan limit | 200 hours | 15 minutes (0.25 hours) | `UsageTrackingService.java:37` |
| Feedback bonus | 50 hours | 5 minutes (0.0833 hours) | `FeedbackController.java:29` |
| Bug report bonus (max) | 50 hours | 5 minutes (0.0833 hours) | `FeedbackController.java:31` |

## How to Revert to Production Values

Search for `TODO: Change back to` in the codebase and update:

### 1. UsageTrackingService.java (line 37)
```java
// Change from:
public static final double FREE_PLAN_HOURS_LIMIT = 0.25; // 15 minutes for testing

// To:
public static final double FREE_PLAN_HOURS_LIMIT = 200.0;
```

### 2. FeedbackController.java (lines 27-31)
```java
// Change from:
private static final double FEEDBACK_BONUS_HOURS = 0.0833; // 5 minutes for testing
private static final double BUG_REPORT_MAX_BONUS = 0.0833; // 5 minutes for testing

// To:
private static final double FEEDBACK_BONUS_HOURS = 50.0;
private static final double BUG_REPORT_MAX_BONUS = 50.0;
```

### 3. FeedbackController.java - Messages
**Line 66:** Change `"5 bonus minutes"` → `"50 bonus hours"`
**Line 111:** Change `"0-5 minutes or extended access"` → `"0-50 hours or 1 month free subscription"`

## Testing Checklist

- [ ] Start a container and verify it stops after ~15 minutes
- [ ] Submit feedback and verify +5 minutes bonus is awarded
- [ ] Check that limit enforcement blocks deployment when exceeded
- [ ] Verify warning banners appear at correct thresholds:
  - Blue (< 75% = < 11.25 minutes)
  - Yellow (75-94% = 11.25-14.1 minutes)
  - Orange (95-99% = 14.25-14.85 minutes)
  - Red (100% = 15+ minutes)

## Quick Revert Command

```bash
# Search for all TODO comments
grep -r "TODO: Change back to" src/
```

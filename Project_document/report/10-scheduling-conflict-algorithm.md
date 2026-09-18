# Scheduling Conflict Algorithm

## The overlap test
Two time intervals `[startA, endA)` and `[startB, endB)` overlap if and only if:

```
startA < endB   AND   startB < endA
```

This single inequality pair is the entire algorithm -- no special-casing is needed for
"new show starts during an existing show", "new show ends during an existing show", or
"new show fully contains an existing show": all three satisfy the inequality, and a
genuinely non-overlapping, back-to-back pair (`endA == startB`) does not, since the
comparison is strict (`<`, not `<=`).

## Implementation
`ShowtimeDao.findOverlapping(screenId, start, end, excludeShowtimeId)` runs:
```sql
SELECT ... FROM showtimes
WHERE screen_id = ? AND status = 'SCHEDULED'
  AND start_time < ?   -- existing.start < new.end
  AND ?  < end_time    -- new.start < existing.end
```
`SchedulingService.createShowtime` calls this inside a `BEGIN IMMEDIATE` write
transaction, so a second admin cannot create a conflicting showtime in the gap between
the check and the insert -- the same reasoning as the seat-locking strategy, applied
to showtimes instead of seats.

## Other validations performed before the overlap check
- The movie exists and is `ACTIVE` (an inactive/deactivated movie cannot be scheduled).
- The screen exists and is `ACTIVE`.
- `start` is strictly before `end`.
- The acting user is an admin (`AuthorizationException` otherwise).

## Test coverage
`SchedulingServiceTest` covers: a valid showtime succeeds; an overlapping showtime is
rejected (`ScheduleConflictException`); two back-to-back, non-overlapping showtimes
are both accepted (proves the boundary condition is handled correctly, not just the
obvious overlap case); an invalid time window is rejected; a non-admin user is
rejected.

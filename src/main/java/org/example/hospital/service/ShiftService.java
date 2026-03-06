package  org.example.hospital.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.example.hospital.domain.Department;
import org.example.hospital.domain.RoleType;
import org.example.hospital.domain.Shift;
import org.example.hospital.domain.ShiftStatus;
import org.example.hospital.domain.UserAccount;
import org.example.hospital.dto.CreateShiftRequest;
import org.example.hospital.dto.ShiftResponse;
import org.example.hospital.dto.ShiftSummaryResponse;
import org.example.hospital.dto.UpdateShiftAssignmentRequest;
import org.example.hospital.dto.UpdateShiftRequest;
import org.example.hospital.dto.SummaryItem;
import org.example.hospital.repository.ShiftRepository;
import org.example.hospital.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ShiftService {

    private static final Logger logger = LoggerFactory.getLogger(ShiftService.class);
    private final ShiftRepository shiftRepository;
    private final DepartmentService departmentService;
    private final UserAccountRepository userAccountRepository;
    private final RealtimePublisher realtimePublisher;

    public ShiftService(ShiftRepository shiftRepository,
                        DepartmentService departmentService,
                        UserAccountRepository userAccountRepository,
                        RealtimePublisher realtimePublisher) {
        this.shiftRepository = shiftRepository;
        this.departmentService = departmentService;
        this.userAccountRepository = userAccountRepository;
        this.realtimePublisher = realtimePublisher;
    }

    @Transactional
    public ShiftResponse createShift(CreateShiftRequest request) {
        logger.info("创建班次: 科室={}, 开始={}, 结束={}",
            request.getDepartmentId(), request.getStartTime(), request.getEndTime());
        validateShiftTimes(request.getStartTime(), request.getEndTime());
        Department department = departmentService.requireById(request.getDepartmentId());
        Shift shift = new Shift(request.getStartTime(), request.getEndTime(), request.getRequiredRole(), department);
        shift.setNotes(request.getNotes());
        Shift saved = shiftRepository.save(shift);
        logger.info("班次创建成功: ID={}", saved.getId());
        ShiftResponse response = toResponse(saved);
        realtimePublisher.publishShiftEvent("SHIFT_CREATED", response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> findAll() {
        logger.debug("查询所有班次");
        return shiftRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ShiftResponse findById(Long id) {
        logger.debug("查询班次: {}", id);
        Shift shift = shiftRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("班次不存在: {}", id);
                    return new IllegalArgumentException("Shift not found");
                });
        return toResponse(shift);
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> findOpenShifts() {
        logger.debug("查询开放班次");
        return shiftRepository.findByStatus(ShiftStatus.OPEN).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> findByDepartmentAndRange(Long departmentId, LocalDateTime start, LocalDateTime end) {
        logger.debug("查询科室班次范围: 科室={}, 开始={}, 结束={}", departmentId, start, end);
        Department department = departmentService.requireById(departmentId);
        LocalDateTime effectiveStart = start != null ? start : LocalDateTime.now().minusDays(7);
        LocalDateTime effectiveEnd = end != null ? end : LocalDateTime.now().plusDays(30);
        return shiftRepository.findByDepartmentAndStartTimeBetween(department, effectiveStart, effectiveEnd)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ShiftResponse updateAssignment(Long shiftId, UpdateShiftAssignmentRequest request) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Shift not found"));
        if (request.getAssigneeUserId() != null) {
            UserAccount assignee = userAccountRepository.findById(request.getAssigneeUserId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            ensureRoleMatches(assignee, shift.getRequiredRole());
            shift.setAssignedUser(assignee);
        } else {
            shift.setAssignedUser(null);
        }
        shift.setNotes(request.getNotes());
        shift.setStatus(request.getStatus());
        ShiftResponse response = toResponse(shift);
        realtimePublisher.publishShiftEvent("SHIFT_UPDATED", response);
        return response;
    }

    @Transactional
    public ShiftResponse updateShiftDetails(Long shiftId, UpdateShiftRequest request) {
        logger.info("管理员更新班次: shiftId={}, departmentId={}, assigneeUserId={}, requiredRole={}, status={}, start={}, end={}",
                shiftId,
                request.getDepartmentId(),
                request.getAssigneeUserId(),
                request.getRequiredRole(),
                request.getStatus(),
                request.getStartTime(),
                request.getEndTime());
        validateShiftTimes(request.getStartTime(), request.getEndTime());
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Shift not found"));
        Department department = departmentService.requireById(request.getDepartmentId());
        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        shift.setRequiredRole(request.getRequiredRole());
        shift.setStatus(request.getStatus());
        shift.setDepartment(department);
        shift.setNotes(request.getNotes());

        if (request.getAssigneeUserId() != null) {
            UserAccount assignee = userAccountRepository.findById(request.getAssigneeUserId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            ensureRoleMatches(assignee, request.getRequiredRole());
            shift.setAssignedUser(assignee);
        } else {
            shift.setAssignedUser(null);
        }

        ShiftResponse response = toResponse(shift);
        logger.info("管理员更新班次成功: shiftId={}, status={}, assigneeUserId={}",
                response.getId(), response.getStatus(), response.getAssigneeUserId());
        realtimePublisher.publishShiftEvent("SHIFT_UPDATED", response);
        return response;
    }

    @Transactional
    public void deleteShift(Long shiftId) {
        if (!shiftRepository.existsById(shiftId)) {
            throw new IllegalArgumentException("Shift not found");
        }
        shiftRepository.deleteById(shiftId);
        realtimePublisher.publishShiftEvent("SHIFT_DELETED", Map.of("shiftId", shiftId));
    }

    @Transactional(readOnly = true)
    public ShiftSummaryResponse buildSummary(LocalDateTime start, LocalDateTime end) {
        LocalDateTime effectiveStart = start != null ? start : LocalDateTime.now().minusDays(7);
        LocalDateTime effectiveEnd = end != null ? end : LocalDateTime.now().plusDays(30);
        List<Shift> rangeShifts = shiftRepository.findByStartTimeBetween(effectiveStart, effectiveEnd);

        long totalShifts = rangeShifts.size();
        long nightShifts = rangeShifts.stream().filter(shift -> isNightShift(shift.getStartTime(), shift.getEndTime())).count();
        long assignedShifts = rangeShifts.stream().filter(shift -> shift.getAssignedUser() != null).count();
        long unassignedShifts = totalShifts - assignedShifts;
        long totalAssignees = rangeShifts.stream()
                .map(Shift::getAssignedUser)
                .filter(Objects::nonNull)
                .map(UserAccount::getId)
                .distinct()
                .count();

        List<SummaryItem> roleDistribution = rangeShifts.stream()
                .collect(Collectors.groupingBy(Shift::getRequiredRole, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Enum::name)))
                .map(entry -> new SummaryItem(entry.getKey().name(), entry.getValue()))
                .collect(Collectors.toList());

        List<SummaryItem> departmentDistribution = rangeShifts.stream()
                .filter(shift -> shift.getDepartment() != null)
                .collect(Collectors.groupingBy(shift -> shift.getDepartment().getName(), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new SummaryItem(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        List<SummaryItem> assigneeDistribution = rangeShifts.stream()
                .filter(shift -> shift.getAssignedUser() != null)
                .collect(Collectors.groupingBy(shift -> shift.getAssignedUser().getFullName(), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(entry -> new SummaryItem(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        return new ShiftSummaryResponse(
                totalShifts,
                nightShifts,
                assignedShifts,
                unassignedShifts,
                totalAssignees,
                roleDistribution,
                departmentDistribution,
                assigneeDistribution
        );
    }

    private boolean isNightShift(LocalDateTime start, LocalDateTime end) {
        if (start == null) {
            return false;
        }
        if (isNightTime(start.toLocalTime())) {
            return true;
        }
        return end != null && isNightTime(end.toLocalTime());
    }

    private boolean isNightTime(LocalTime time) {
        return time.isAfter(LocalTime.of(17, 59)) || time.isBefore(LocalTime.of(6, 0));
    }

    private void validateShiftTimes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Shift time cannot be null");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Shift end must be after start");
        }
    }

    private void ensureRoleMatches(UserAccount userAccount, RoleType requiredRole) {
        boolean hasRole = userAccount.getRoles().stream()
                .map(role -> role.getName())
                .filter(Objects::nonNull)
                .anyMatch(roleType -> roleType == requiredRole);
        if (!hasRole) {
            logger.warn("班次指派失败: userId={}, fullName={}, requiredRole={}, actualRoles={}",
                    userAccount.getId(),
                    userAccount.getFullName(),
                    requiredRole,
                    userAccount.getRoles().stream()
                            .map(role -> role.getName() != null ? role.getName().name() : "NULL")
                            .collect(Collectors.toList()));
            throw new IllegalArgumentException("User does not have the required role");
        }
    }

    private ShiftResponse toResponse(Shift shift) {
        Long assigneeId = shift.getAssignedUser() != null ? shift.getAssignedUser().getId() : null;
        String assigneeName = shift.getAssignedUser() != null ? shift.getAssignedUser().getFullName() : null;
        String shiftType = isNightShift(shift.getStartTime(), shift.getEndTime()) ? "NIGHT" : "DAY";
        return new ShiftResponse(
                shift.getId(),
                shift.getStartTime(),
                shift.getEndTime(),
                shiftType,
                shift.getRequiredRole(),
                shift.getStatus(),
                shift.getDepartment().getId(),
                shift.getDepartment().getName(),
                assigneeId,
                assigneeName,
                shift.getNotes()
        );
    }
}

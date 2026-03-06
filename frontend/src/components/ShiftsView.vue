<template>
  <div class="shifts-page">
    <header class="header shifts-header">
      <h1>班次管理</h1>
      <button class="btn-refresh" style="width: auto;" @click="$emit('refresh')">刷新</button>
    </header>

    <div class="stats">
      <div class="stat-card">
        <div class="stat-title">总班次</div>
        <div class="stat-value">{{ filteredShifts.length }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-title">已指派</div>
        <div class="stat-value" style="color: #10b981;">{{ assignedCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-title">待指派</div>
        <div class="stat-value" style="color: #f59e0b;">{{ unassignedCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-title">夜班</div>
        <div class="stat-value" style="color: #6366f1;">{{ nightShiftCount }}</div>
      </div>
    </div>

    <div class="filter-row">
      <div class="filter-group">
        <label>科室</label>
        <select v-model="filterDeptId" class="filter-select">
          <option value="">全部科室</option>
          <option v-for="dept in departments" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
        </select>
      </div>
      <div class="filter-group">
        <label>状态</label>
        <select v-model="filterStatus" class="filter-select">
          <option value="">全部状态</option>
          <option v-for="status in statusOptions" :key="status" :value="status">{{ statusLabel(status) }}</option>
        </select>
      </div>
      <div class="filter-group">
        <label>班次</label>
        <select v-model="filterShiftType" class="filter-select">
          <option value="">全部班次</option>
          <option value="DAY">白班</option>
          <option value="NIGHT">夜班</option>
        </select>
      </div>
      <div class="filter-actions">
        <button class="btn-outline" @click="resetFilters">重置筛选</button>
      </div>
    </div>

    <div class="shifts-layout">
      <div class="card table-card">
        <div class="table-wrap">
          <table class="shift-table table">
            <thead>
              <tr>
                <th>ID</th>
                <th>时间范围</th>
                <th>科室</th>
                <th>必需角色</th>
                <th>状态</th>
                <th v-if="isAdmin">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td :colspan="isAdmin ? 6 : 5" class="text-center">加载中...</td></tr>
              <tr v-else-if="filteredShifts.length === 0"><td :colspan="isAdmin ? 6 : 5" class="text-center">暂无排班</td></tr>
              <tr v-for="shift in filteredShifts" :key="shift.id">
                <td>{{ shift.id }}</td>
                <td>
                  {{ formatTime(shift.startTime) }} <br />
                  <span class="text-muted text-sm">{{ formatTime(shift.endTime) }}</span>
                </td>
                <td>{{ shift.departmentName || shift.departmentId || '-' }}</td>
                <td><span class="chip">{{ shift.requiredRole || '未指定' }}</span></td>
                <td>
                  <span :class="['status-badge', `status-${shift.status || 'OPEN'}`]">{{ statusLabel(shift.status) }}</span>
                </td>
                <td v-if="isAdmin">
                  <button class="btn-edit" @click="openEditModal(shift)">编辑</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="card distribution-card">
        <div class="panel-title">班次类型占比</div>
        <div class="pie-3d-wrapper">
          <div class="pie-3d-container">
            <div class="pie-3d" :style="pieChartStyle"></div>
            <div class="pie-3d-thickness" :style="pieChartThicknessStyle"></div>
          </div>
          <div class="pie-legend-list">
             <div class="legend-item">
               <span class="legend-dot" style="background: #a855f7;"></span>
               <span>白班 {{ dayShiftCount }}</span>
             </div>
             <div class="legend-item">
               <span class="legend-dot" style="background: #312e81;"></span>
               <span>夜班 {{ nightShiftCount }}</span>
             </div>
          </div>
        </div>
        <div class="divider"></div>

        <div class="panel-title">人员分布（本月）</div>
        <div class="text-muted text-sm">按被指派次数统计</div>
        <div class="bar-list">
          <div v-for="item in assigneeDistribution" :key="item.label" class="bar-row">
            <div class="bar-label">{{ item.label }}</div>
            <div class="bar-track">
              <div class="bar-fill" :style="{ width: barWidth(item.value) }"></div>
            </div>
            <div class="bar-value">{{ item.value }}</div>
          </div>
          <div v-if="assigneeDistribution.length === 0" class="text-muted text-sm">暂无人员分布</div>
        </div>
        <div class="divider"></div>
        <div class="panel-title">科室分布（本月）</div>
        <div class="dept-list">
          <div v-for="item in departmentDistribution" :key="item.label" class="dept-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
          <div v-if="departmentDistribution.length === 0" class="text-muted text-sm">暂无科室分布</div>
        </div>
      </div>
    </div>

    <div v-if="showEditModal" class="modal-mask" @click.self="closeEditModal">
      <div class="modal-panel">
        <div class="modal-header">
          <h3>编辑班次 #{{ editForm.id }}</h3>
          <button class="modal-close" @click="closeEditModal">×</button>
        </div>
        <div class="modal-body">
          <label>科室ID</label>
          <input v-model="editForm.departmentId" type="number" min="1" />

          <label>指派用户ID</label>
          <input v-model="editForm.assigneeUserId" type="number" min="1" placeholder="可留空" />

          <label>必需角色</label>
          <input v-model="editForm.requiredRole" type="text" />

          <label>班次类型</label>
          <select v-model="editForm.shiftType">
            <option value="DAY">DAY</option>
            <option value="NIGHT">NIGHT</option>
          </select>

          <label>状态</label>
          <select v-model="editForm.status">
            <option value="OPEN">OPEN</option>
            <option value="ASSIGNED">ASSIGNED</option>
            <option value="COMPLETED">COMPLETED</option>
            <option value="CANCELLED">CANCELLED</option>
          </select>

          <label>开始时间</label>
          <input v-model="editForm.startTime" type="datetime-local" />

          <label>结束时间</label>
          <input v-model="editForm.endTime" type="datetime-local" />

          <label>备注</label>
          <input v-model="editForm.notes" type="text" placeholder="可选，填写本次调整说明" />

          <p v-if="editError" class="form-error">{{ editError }}</p>
        </div>
        <div class="modal-footer">
          <button class="btn-ghost" @click="closeEditModal">取消</button>
          <button class="btn-primary" :disabled="savingEdit" @click="submitEdit">{{ savingEdit ? '保存中...' : '保存' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';

const props = defineProps({
  shifts: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  departments: { type: Array, default: () => [] },
  assigneeDistribution: { type: Array, default: () => [] },
  departmentDistribution: { type: Array, default: () => [] },
  isAdmin: { type: Boolean, default: false }
});

const emit = defineEmits(['refresh', 'edit-shift']);

const filterDeptId = ref('');
const filterStatus = ref('');
const filterShiftType = ref('');

const statusOptions = ['OPEN', 'ASSIGNED', 'COMPLETED', 'CANCELLED'];
const statusLabel = (status) => {
  const map = {
    OPEN: '待指派',
    ASSIGNED: '已指派',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    PENDING: '待指派'
  };
  return map[status] || (status ? status.replace(/_/g, ' ') : '未知');
};

const filteredShifts = computed(() => {
  return (props.shifts || []).filter(shift => {
    if (filterDeptId.value && String(shift.departmentId) !== String(filterDeptId.value)) return false;
    if (filterStatus.value && shift.status !== filterStatus.value) return false;
    if (filterShiftType.value && shift.shiftType !== filterShiftType.value) return false;
    return true;
  });
});

const assignedCount = computed(() => filteredShifts.value.filter(s => !!s.assigneeUserId).length);
const unassignedCount = computed(() => filteredShifts.value.filter(s => !s.assigneeUserId).length);
const nightShiftCount = computed(() => filteredShifts.value.filter(s => s.shiftType === 'NIGHT').length);
const dayShiftCount = computed(() => filteredShifts.value.length - nightShiftCount.value);

const resetFilters = () => {
  filterDeptId.value = '';
  filterStatus.value = '';
  filterShiftType.value = '';
};

const pieChartStyle = computed(() => {
  const total = filteredShifts.value.length || 1;
  const nightPercent = (nightShiftCount.value / total) * 100;
  // 夜班深紫色 #312e81，白班浅紫色 #a855f7
  return {
    background: `conic-gradient(
      #312e81 0% ${nightPercent}%, 
      #a855f7 ${nightPercent}% 100%
    )`
  };
});

// Calculate thickness color roughly darker
const pieChartThicknessStyle = computed(() => {
    const total = filteredShifts.value.length || 1;
    const nightPercent = (nightShiftCount.value / total) * 100;
    // Darker shades for thickness
    return {
      background: `conic-gradient(
        #1e1b4b 0% ${nightPercent}%, 
        #7e22ce ${nightPercent}% 100%
      )`
    };
});

const formatTime = (isoString) => {
  if (!isoString) return '-';
  return new Date(isoString).toLocaleString();
};

const barWidth = (value) => {
  const values = props.assigneeDistribution.map(item => item.value || 0);
  const max = Math.max(1, ...values);
  const ratio = Math.min(1, (value || 0) / max);
  return `${Math.round(ratio * 100)}%`;
};

// Admin edit modal logic
const showEditModal = ref(false);
const savingEdit = ref(false);
const editError = ref('');
const editForm = reactive({
  id: null,
  departmentId: '',
  assigneeUserId: '',
  requiredRole: '',
  shiftType: 'DAY',
  status: 'OPEN',
  startTime: '',
  endTime: '',
  notes: ''
});

const toLocalInput = (isoString) => {
  if (!isoString) return '';
  const d = new Date(isoString);
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

const toIsoOrNull = (val) => {
  if (!val) return null;
  const d = new Date(val);
  if (Number.isNaN(d.getTime())) return null;
  return d.toISOString();
};

const openEditModal = (shift) => {
  editError.value = '';
  editForm.id = shift.id;
  editForm.departmentId = shift.departmentId ?? '';
  editForm.assigneeUserId = shift.assigneeUserId ?? '';
  editForm.requiredRole = shift.requiredRole ?? '';
  editForm.shiftType = shift.shiftType || 'DAY';
  editForm.status = shift.status || 'OPEN';
  editForm.startTime = toLocalInput(shift.startTime);
  editForm.endTime = toLocalInput(shift.endTime);
  editForm.notes = shift.notes ?? '';
  showEditModal.value = true;
};

const closeEditModal = () => {
  showEditModal.value = false;
  savingEdit.value = false;
  editError.value = '';
};

const submitEdit = async () => {
  editError.value = '';
  if (!editForm.id || !editForm.departmentId) {
    editError.value = '请补全班次ID和科室ID';
    return;
  }
  const start = toIsoOrNull(editForm.startTime);
  const end = toIsoOrNull(editForm.endTime);
  if (!start || !end || new Date(start).getTime() >= new Date(end).getTime()) {
    editError.value = '时间范围不合法';
    return;
  }

  savingEdit.value = true;
  try {
    await emit('edit-shift', {
      id: Number(editForm.id),
      departmentId: Number(editForm.departmentId),
      assigneeUserId: editForm.assigneeUserId ? Number(editForm.assigneeUserId) : null,
      requiredRole: editForm.requiredRole || null,
      shiftType: editForm.shiftType || 'DAY',
      status: editForm.status || 'OPEN',
      startTime: start,
      endTime: end,
      notes: editForm.notes?.trim() || null
    });
    closeEditModal();
  } catch (e) {
    editError.value = e?.message || '保存失败';
  } finally {
    savingEdit.value = false;
  }
};
</script>

<style scoped>
.btn-edit { border: 1px solid #9a8cff; color: #5b49d6; background: #fff; border-radius: 10px; padding: 6px 10px; cursor: pointer; }
.modal-mask { position: fixed; inset: 0; background: rgba(27, 21, 63, 0.25); display: flex; align-items: center; justify-content: center; z-index: 60; }
.modal-panel { width: min(640px, 92vw); background: #fff; border-radius: 16px; box-shadow: 0 20px 60px rgba(66, 44, 160, 0.25); overflow: hidden; }
.modal-header { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; border-bottom: 1px solid #eee8ff; }
.modal-close { border: none; background: transparent; font-size: 22px; cursor: pointer; color: #6b5bd2; }
.modal-body { display: grid; gap: 8px; padding: 16px; }
.modal-body input, .modal-body select { border: 1px solid #dcd4ff; border-radius: 10px; padding: 9px 10px; }
.form-error { color: #d23b5f; font-size: 13px; margin-top: 4px; }
.modal-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 16px 16px; }
.btn-ghost { background: #fff; border: 1px solid #cdc5ff; color: #5b49d6; border-radius: 10px; padding: 8px 14px; }
.btn-primary { background: linear-gradient(135deg, #9b8bff, #7b6dff); border: none; color: #fff; border-radius: 10px; padding: 8px 14px; }
</style>

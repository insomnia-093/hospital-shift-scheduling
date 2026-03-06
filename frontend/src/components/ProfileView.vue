<template>
  <header class="header">
    <h1>个人中心</h1>
    <button style="width: auto;" class="secondary" @click="$emit('navigate', 'dashboard')">返回概览</button>
  </header>
  <div class="profile-grid">
    <div class="card profile-panel">
      <div class="profile-header">
        <div class="avatar avatar-lg">{{ userInitials }}</div>
        <div>
          <h2>{{ user.fullName || '未命名用户' }}</h2>
          <div class="text-muted">{{ user.email || '暂无邮箱' }}</div>
          <div class="tag-row">
            <span v-for="role in (user.roles || [])" :key="role" class="tag">{{ role }}</span>
          </div>
        </div>
      </div>
      <div class="info-list">
        <div class="info-item">
          <span>账号ID</span>
          <strong>{{ user.id || '-' }}</strong>
        </div>
        <div class="info-item">
          <span>上次登录</span>
          <strong>{{ formatTime(lastLogin) }}</strong>
        </div>
      </div>
    </div>

    <div class="card profile-panel">
      <h3>账号安全</h3>
      <div class="info-list">
        <div class="info-item">
          <span>安全状态</span>
          <strong>正常</strong>
        </div>
        <div class="info-item">
          <span>权限摘要</span>
          <strong>{{ (user.roles || []).length }} 项</strong>
        </div>
      </div>
      <div class="notice">
        如需修改密码或角色，请联系系统管理员。
      </div>
    </div>

    <div class="card profile-panel">
      <h3>快捷入口</h3>
      <div class="action-grid">
        <button class="secondary" @click="$emit('navigate', 'shifts')">查看排班</button>
        <button class="secondary" @click="$emit('navigate', 'agent')">智能体任务</button>
        <button class="secondary" @click="$emit('navigate', 'dashboard')">返回概览</button>
      </div>
    </div>
  </div>

  <div v-if="isAdmin" class="profile-grid" style="margin-top: 1.5rem;">
    <div class="card profile-panel">
      <h3>管理员 - 重置用户密码</h3>
      <div class="form-group">
        <label>选择用户</label>
        <select v-model="passwordForm.userId">
          <option value="">请选择用户</option>
          <option v-for="u in adminUsers" :key="u.id" :value="u.id">
            {{ u.fullName }} ({{ u.email }})
          </option>
        </select>
      </div>
      <div class="form-group">
        <label>新密码</label>
        <input type="password" v-model="passwordForm.newPassword" placeholder="至少 8 位" />
      </div>
      <button style="width: auto;" @click="resetUserPassword" :disabled="loading">更新密码</button>
    </div>

    <div class="card profile-panel">
      <h3>科室值班统计（夜班）</h3>
      <div class="text-muted text-sm" style="margin-bottom: 1rem;">按科室统计已指派的夜班人数</div>
      <div style="display: flex; gap: 2rem; align-items: center;">
        <div style="flex: 1; text-align: center;">
          <div class="pie-chart" :style="safePieData.style" style="width: 150px; height: 150px; border-radius: 50%; margin: 0 auto;"></div>
        </div>
        <div style="flex: 1;">
          <div class="pie-legend">
            <div v-for="item in safePieData.items" :key="item.label" class="legend-item">
              <span class="legend-color" :style="{ backgroundColor: item.color }"></span>
              <span class="legend-label">{{ item.label }}</span>
              <span class="legend-value">{{ item.value }} 人</span>
            </div>
            <div v-if="safePieData.items.length === 0" class="text-muted text-sm">暂无数据</div>
          </div>
        </div>
      </div>
    </div>

    <section class="admin-section">
      <div class="admin-shifts-card glass-card">
        <div class="admin-shifts-header">
          <h3>排班管理</h3>
        </div>

        <div class="admin-shifts-table-wrap">
          <table class="admin-shifts-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>科室</th>
                <th>班次</th>
                <th>开始</th>
                <th>结束</th>
                <th>状态</th>
                <th>指派</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="shift in shifts" :key="shift.id">
                <td>{{ shift.id }}</td>
                <td>{{ shift.departmentName || shift.departmentId || '-' }}</td>
                <td>{{ shift.shiftType || '-' }}</td>
                <td>{{ shift.startTime || '-' }}</td>
                <td>{{ shift.endTime || '-' }}</td>
                <td>{{ shift.status || '-' }}</td>
                <td>{{ shift.assigneeName || shift.assigneeUserId || '-' }}</td>
                <td>
                  <button class="btn-edit" @click="openShiftEditor(shift)">编辑</button>
                </td>
              </tr>
              <tr v-if="!shifts || shifts.length === 0">
                <td colspan="8" class="empty-row">暂无排班数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="showShiftModal" class="modal-mask" @click.self="closeShiftEditor">
        <div class="modal-panel">
          <div class="modal-header">
            <h3>编辑排班 #{{ editForm.id }}</h3>
            <button class="modal-close" @click="closeShiftEditor">×</button>
          </div>

          <div class="modal-body">
            <label>科室ID</label>
            <input v-model="editForm.departmentId" type="number" min="1" />

            <label>指派用户ID</label>
            <input v-model="editForm.assigneeUserId" type="number" min="1" placeholder="可留空" />

            <label>必需角色</label>
            <input v-model="editForm.requiredRole" type="text" placeholder="如 DOCTOR / NURSE" />

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
            <button class="btn-ghost" @click="closeShiftEditor">取消</button>
            <button class="btn-primary" :disabled="savingShift" @click="submitShiftEdit">
              {{ savingShift ? '保存中...' : '保存修改' }}
            </button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, computed, ref } from 'vue';

const props = defineProps({
  user: { type: Object, default: () => ({}) },
  lastLogin: { type: String, default: '' },
  isAdmin: { type: Boolean, default: false },
  adminUsers: { type: Array, default: () => [] },
  shifts: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  pieData: { type: Object, default: () => ({ items: [], style: {} }) }
});

const emit = defineEmits(['navigate', 'reset-password', 'update-shift']);

const passwordForm = reactive({ userId: '', newPassword: '' });
const showShiftModal = ref(false);
const savingShift = ref(false);
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

const userInitials = computed(() => {
  const name = (props.user.fullName || props.user.email || 'U').trim();
  if (!name) return 'U';
  if (name.length <= 2) return name.toUpperCase();
  return name.slice(0, 2).toUpperCase();
});

const formatTime = (isoString) => {
  if (!isoString) return '-';
  return new Date(isoString).toLocaleString();
};

const resetUserPassword = () => {
  if (!passwordForm.userId || !passwordForm.newPassword) return;
  emit('reset-password', { ...passwordForm });
  passwordForm.newPassword = '';
};

const safePieData = computed(() => ({
  items: Array.isArray(props.pieData?.items) ? props.pieData.items : [],
  style: props.pieData?.style || {}
}));

const toLocalInput = (isoString) => {
  if (!isoString) return '';
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (val) => String(val).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

const openShiftEditor = (shift) => {
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
  showShiftModal.value = true;
};

const closeShiftEditor = () => {
  showShiftModal.value = false;
  savingShift.value = false;
  editError.value = '';
};

const submitShiftEdit = async () => {
  editError.value = '';
  if (!editForm.id) {
    editError.value = '排班ID缺失';
    return;
  }
  if (!editForm.departmentId) {
    editError.value = '请选择科室ID';
    return;
  }

  const start = toIsoOrNull(editForm.startTime);
  const end = toIsoOrNull(editForm.endTime);
  if (!start || !end) {
    editError.value = '开始/结束时间格式不正确';
    return;
  }
  if (new Date(start).getTime() >= new Date(end).getTime()) {
    editError.value = '结束时间必须晚于开始时间';
    return;
  }

  savingShift.value = true;
  try {
    await emit('update-shift', {
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
    closeShiftEditor();
  } catch (e) {
    editError.value = e?.message || '保存失败';
  } finally {
    savingShift.value = false;
  }
};

const toIsoOrNull = (v) => {
  if (!v) return null;
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return null;
  return d.toISOString();
};
</script>

<style scoped>
.header { padding: 16px; background: #f5f5f5; border-bottom: 1px solid #e0e0e0; }
.profile-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 16px; margin: 16px 0; }
.card { background: #fff; border-radius: 12px; box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1); padding: 16px; }
.profile-panel { padding: 16px; }
.profile-header { display: flex; align-items: center; margin-bottom: 16px; }
.avatar { width: 56px; height: 56px; line-height: 56px; text-align: center; border-radius: 50%; background: #9a8cff; color: #fff; font-weight: bold; }
.info-list { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.info-item { display: flex; justify-content: space-between; padding: 8px 0; }
.tag-row { margin-top: 8px; }
.tag { display: inline-block; padding: 4px 8px; background: #e0e0e0; border-radius: 12px; font-size: 12px; margin-right: 4px; }
.secondary { background: #6b5bd2; color: #fff; border: none; border-radius: 10px; padding: 10px 16px; cursor: pointer; }
.secondary:hover { background: #5b49d6; }
.notice { background: #fff3cd; color: #856404; padding: 12px; border-radius: 8px; margin-top: 16px; }
.admin-section { margin-top: 32px; }
.admin-shifts-card { margin-top: 16px; padding: 16px; border-radius: 16px; }
.admin-shifts-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.admin-shifts-table-wrap { overflow: auto; max-height: 420px; }
.admin-shifts-table { width: 100%; border-collapse: collapse; }
.admin-shifts-table th, .admin-shifts-table td { padding: 10px 8px; border-bottom: 1px solid #ece8ff; text-align: left; white-space: nowrap; }
.empty-row { text-align: center; color: #888; }
.btn-edit { border: 1px solid #9a8cff; color: #5b49d6; background: #fff; border-radius: 10px; padding: 6px 10px; cursor: pointer; }

.modal-mask { position: fixed; inset: 0; background: rgba(27, 21, 63, 0.25); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal-panel { width: min(640px, 92vw); background: #fff; border-radius: 16px; box-shadow: 0 20px 60px rgba(66, 44, 160, 0.25); overflow: hidden; }
.modal-header { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; border-bottom: 1px solid #eee8ff; }
.modal-close { border: none; background: transparent; font-size: 22px; cursor: pointer; color: #6b5bd2; }
.modal-body { display: grid; grid-template-columns: 1fr 1fr; gap: 10px 12px; padding: 16px; }
.modal-body label { grid-column: span 2; font-size: 13px; color: #5f5f78; margin-top: 2px; }
.modal-body input, .modal-body select { grid-column: span 2; }
.form-error { color: #d23b5f; font-size: 13px; margin-top: 6px; }
.modal-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 16px 16px; }
.btn-ghost { background: #fff; border: 1px solid #cdc5ff; color: #5b49d6; border-radius: 10px; padding: 8px 14px; }
.btn-primary { background: linear-gradient(135deg, #9b8bff, #7b6dff); border: none; color: #fff; border-radius: 10px; padding: 8px 14px; }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
</style>

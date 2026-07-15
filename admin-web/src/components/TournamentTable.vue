<template>
  <div class="table-wrap">
    <table v-if="items.length">
      <thead>
        <tr>
          <th>赛事</th>
          <th>项目</th>
          <th>赛制</th>
          <th>状态</th>
          <th>收藏</th>
          <th>创建时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id">
          <td>
            <strong>{{ item.name }}</strong>
            <span>{{ item.location || '未填写地点' }}</span>
          </td>
          <td>{{ sportText(item) }}</td>
          <td>{{ typeText(item.tournamentType) }}</td>
          <td><span class="status">{{ statusText(item.status) }}</span></td>
          <td>{{ item.favoriteCount || 0 }}</td>
          <td>{{ formatTime(item.createTime) }}</td>
        </tr>
      </tbody>
    </table>
    <div v-else class="empty-state">{{ emptyText }}</div>
  </div>
</template>

<script setup>
defineProps({
  items: { type: Array, default: () => [] },
  emptyText: { type: String, default: '暂无数据' },
})

function sportText(item) {
  if (item.sportType === 1) return '排球'
  if (item.participantType === 1 && item.teamMatchTemplate === 2) return '羽毛球接力'
  if (item.participantType === 1) return '羽毛球团体'
  return '羽毛球个人'
}

function typeText(value) {
  if (value === 1) return '小组+淘汰'
  if (value === 2) return '循环赛'
  return '淘汰赛'
}

function statusText(value) {
  if (value === 2) return '已结束'
  if (value === 1) return '进行中'
  return '未开始'
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}
</script>

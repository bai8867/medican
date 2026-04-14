<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 分组：{ key, label, items: { name, amount, note? }[] }[] */
  groups: {
    type: Array,
    default: null,
  },
  /** 未分组时的平铺列表（兼容旧用法） */
  items: {
    type: Array,
    default: () => [],
  },
})

const sections = computed(() => {
  if (props.groups?.length) {
    return props.groups
      .filter((g) => g.items?.length)
      .map((g) => ({ label: g.label || '食材', items: g.items }))
  }
  if (props.items?.length) return [{ label: '', items: props.items }]
  return []
})

const isEmpty = computed(() => !sections.value.length)

function formatAmount(it) {
  const a = it?.amount != null ? String(it.amount).trim() : ''
  return a || '适量'
}
</script>

<template>
  <section class="block">
    <h3 class="block__title">食材清单</h3>
    <template v-if="!isEmpty">
      <div v-for="(sec, si) in sections" :key="si" class="ing-group">
        <h4 v-if="sec.label" class="ing-group__title">{{ sec.label }}</h4>
        <ul class="list">
          <li v-for="(it, idx) in sec.items" :key="`${si}-${idx}`" class="list__row">
            <span class="list__name">{{ it.name }}</span>
            <span
              class="list__amt"
              :class="{ 'list__amt--muted': !it?.amount || !String(it.amount).trim() }"
            >{{ formatAmount(it) }}</span>
            <el-tag v-if="it.note" size="small" type="info" effect="plain">{{ it.note }}</el-tag>
          </li>
        </ul>
      </div>
    </template>
    <el-empty v-else description="数据暂未完善" :image-size="72" />
  </section>
</template>

<style scoped>
.block__title {
  margin: 0 0 var(--space-md);
  font-size: var(--font-size-lg);
}

.ing-group + .ing-group {
  margin-top: var(--space-lg);
}

.ing-group__title {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-text-secondary);
  letter-spacing: 0.02em;
}

.list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.list__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
}

.list__name {
  flex: 1;
  min-width: 120px;
  font-weight: 500;
}

.list__amt {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.list__amt--muted {
  font-style: italic;
}
</style>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  showToast,
  NavBar as VanNavBar,
  Cell as VanCell,
  CellGroup as VanCellGroup,
  Checkbox,
  CheckboxGroup,
  Radio,
  RadioGroup,
  Button as VanButton,
} from 'vant'
import { useUserStore } from '@/stores/user'
import {
  CAMPUS_LOCATION_OPTIONS,
  BUDGET_TIER_OPTIONS,
  ALLERGY_TAG_OPTIONS,
} from '@/data/campusWeeklyCalendarSeed.js'

const router = useRouter()
const userStore = useUserStore()

const campusIds = ref([...(userStore.preferences?.campusLocationIds || [])])
const budgetTier = ref(userStore.preferences?.budgetTier || 'regular')
const allergyIds = ref([...(userStore.preferences?.allergyTags || [])])

const dirty = ref(false)

watch([campusIds, budgetTier, allergyIds], () => {
  dirty.value = true
})

const budgetLabel = computed(() => {
  const hit = BUDGET_TIER_OPTIONS.find((o) => o.id === budgetTier.value)
  return hit?.label || '常规型'
})

function syncFromStore() {
  campusIds.value = [...(userStore.preferences?.campusLocationIds || [])]
  budgetTier.value = userStore.preferences?.budgetTier || 'regular'
  allergyIds.value = [...(userStore.preferences?.allergyTags || [])]
  dirty.value = false
}

watch(
  () => userStore.preferences,
  () => {
    if (!dirty.value) syncFromStore()
  },
  { deep: true },
)

function save() {
  userStore.setPreferences({
    campusLocationIds: [...campusIds.value],
    budgetTier: budgetTier.value,
    allergyTags: [...allergyIds.value],
  })
  dirty.value = false
  showToast('偏好已保存')
}

function onBack() {
  if (dirty.value) save()
  router.back()
}
</script>

<template>
  <div class="pref-page">
    <VanNavBar title="饮食偏好" left-arrow fixed placeholder @click-left="onBack" />

    <p class="pref-page__intro">
      以下信息与体质、季节画像合并用于首页、场景页与本周药膳日历的排序与过滤。
    </p>

    <VanCellGroup inset title="常去校区 / 食堂（多选）">
      <VanCell>
        <CheckboxGroup v-model="campusIds" direction="horizontal" class="pref-page__tags">
          <Checkbox
            v-for="opt in CAMPUS_LOCATION_OPTIONS"
            :key="opt.id"
            :name="opt.id"
            shape="square"
            class="pref-page__cb"
          >
            {{ opt.label }}
          </Checkbox>
        </CheckboxGroup>
      </VanCell>
    </VanCellGroup>

    <VanCellGroup inset title="预算档位" class="pref-page__block">
      <VanCell title="当前选择" :value="budgetLabel" />
      <VanCell>
        <RadioGroup v-model="budgetTier" direction="horizontal">
          <Radio v-for="opt in BUDGET_TIER_OPTIONS" :key="opt.id" :name="opt.id" class="pref-page__radio">
            {{ opt.label }}
          </Radio>
        </RadioGroup>
      </VanCell>
    </VanCellGroup>

    <VanCellGroup inset title="忌口 / 过敏源（多选）" class="pref-page__block">
      <VanCell>
        <CheckboxGroup v-model="allergyIds" direction="horizontal" class="pref-page__tags pref-page__tags--wrap">
          <Checkbox
            v-for="opt in ALLERGY_TAG_OPTIONS"
            :key="opt.id"
            :name="opt.id"
            shape="square"
            class="pref-page__cb"
          >
            {{ opt.label }}
          </Checkbox>
        </CheckboxGroup>
      </VanCell>
    </VanCellGroup>

    <div class="pref-page__actions">
      <VanButton type="primary" block round @click="save">保存偏好</VanButton>
    </div>
  </div>
</template>

<style scoped>
.pref-page {
  min-height: 100%;
  padding-bottom: 32px;
  background: var(--color-bg-page, #f7f7f7);
}

.pref-page__intro {
  margin: 12px 16px 8px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--color-text-secondary);
}

.pref-page__block {
  margin-top: 12px;
}

.pref-page__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  padding: 4px 0;
}

.pref-page__tags--wrap {
  gap: 10px 14px;
}

.pref-page__cb {
  margin: 0;
}

.pref-page__radio {
  margin-right: 12px;
}

.pref-page__actions {
  padding: 20px 16px 8px;
}
</style>

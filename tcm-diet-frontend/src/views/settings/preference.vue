<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  NavBar as VanNavBar,
  CellGroup as VanCellGroup,
  Cell as VanCell,
  Switch as VanSwitch,
  CheckboxGroup as VanCheckboxGroup,
  Checkbox as VanCheckbox,
  RadioGroup as VanRadioGroup,
  Radio as VanRadio,
} from 'vant'
import { useUserStore } from '@/stores/user'
import {
  CAMPUS_LOCATION_OPTIONS,
  ALLERGY_TAG_OPTIONS,
  BUDGET_TIER_OPTIONS,
} from '@/data/campusWeeklyCalendarSeed'

const router = useRouter()
const userStore = useUserStore()

const avoidSpicy = computed({
  get: () => Boolean(userStore.preferences.avoidSpicy),
  set: (v: boolean) => userStore.setPreferences({ avoidSpicy: v }),
})

const avoidCold = computed({
  get: () => Boolean(userStore.preferences.avoidCold),
  set: (v: boolean) => userStore.setPreferences({ avoidCold: v }),
})

const campusLocationIds = computed({
  get: () => [...(userStore.preferences.campusLocationIds || [])],
  set: (v: string[]) => userStore.setPreferences({ campusLocationIds: [...v] }),
})

const allergyTags = computed({
  get: () => [...(userStore.preferences.allergyTags || [])],
  set: (v: string[]) => userStore.setPreferences({ allergyTags: [...v] }),
})

const budgetTier = computed({
  get: () => String(userStore.preferences.budgetTier || 'regular'),
  set: (v: string) => userStore.setPreferences({ budgetTier: v }),
})
</script>

<template>
  <div class="settings-sub settings-sub--scroll">
    <van-nav-bar title="饮食偏好" left-arrow fixed placeholder @click-left="router.back()" />
    <div class="body">
      <p class="hint">用于首页推荐与校园药膳日历的过滤与排序，数据保存在本机画像中。</p>

      <van-cell-group inset title="口味与禁忌">
        <van-cell title="尽量避开辛辣" center>
          <template #right-icon>
            <van-switch v-model="avoidSpicy" size="20px" />
          </template>
        </van-cell>
        <van-cell title="尽量避开生冷" center>
          <template #right-icon>
            <van-switch v-model="avoidCold" size="20px" />
          </template>
        </van-cell>
      </van-cell-group>

      <van-cell-group inset title="常去校区 / 食堂">
        <van-cell>
          <van-checkbox-group v-model="campusLocationIds" class="chk-grid">
            <van-checkbox
              v-for="o in CAMPUS_LOCATION_OPTIONS"
              :key="o.id"
              :name="o.id"
              shape="square"
              class="chk-item"
            >
              {{ o.label }}
            </van-checkbox>
          </van-checkbox-group>
        </van-cell>
      </van-cell-group>

      <van-cell-group inset title="预算档位">
        <van-radio-group v-model="budgetTier">
          <van-cell v-for="o in BUDGET_TIER_OPTIONS" :key="o.id" clickable @click="budgetTier = o.id">
            <template #title>
              <van-radio :name="o.id">{{ o.label }}</van-radio>
            </template>
          </van-cell>
        </van-radio-group>
      </van-cell-group>

      <van-cell-group inset title="忌口 / 过敏（关键词匹配）">
        <van-cell>
          <van-checkbox-group v-model="allergyTags" class="chk-grid">
            <van-checkbox
              v-for="o in ALLERGY_TAG_OPTIONS"
              :key="o.id"
              :name="o.id"
              shape="square"
              class="chk-item"
            >
              {{ o.label }}
            </van-checkbox>
          </van-checkbox-group>
        </van-cell>
      </van-cell-group>
    </div>
  </div>
</template>

<style scoped>
.body {
  padding-bottom: 24px;
}

.hint {
  margin: 12px 16px;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.chk-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  padding: 4px 0;
}

.chk-item {
  margin: 0;
}
</style>

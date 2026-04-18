<script setup>
import { computed } from 'vue'
import {
  DropdownMenu as VanDropdownMenu,
  DropdownItem as VanDropdownItem,
  Field as VanField,
  RadioGroup as VanRadioGroup,
  Radio as VanRadio,
  Icon as VanIcon,
} from 'vant'

const props = defineProps({
  effectFilter: { type: String, required: true },
  constitutionFilter: { type: String, required: true },
  sortBy: { type: String, required: true },
  recipeSearchInput: { type: String, required: true },
  effectFilterOptions: { type: Array, required: true },
  constitutionFilterOptions: { type: Array, required: true },
  efficacyAllValue: { type: String, required: true },
  constitutionAllValue: { type: String, required: true },
  recipeSearchFallbackToKeywordOnly: { type: Boolean, default: false },
  recipeSearchKeyword: { type: String, default: '' },
})

const emit = defineEmits([
  'update:effectFilter',
  'update:constitutionFilter',
  'update:sortBy',
  'update:recipeSearchInput',
  'searchEnter',
  'searchClear',
])

const effectModel = computed({
  get: () => props.effectFilter,
  set: (v) => emit('update:effectFilter', v),
})

const constitutionModel = computed({
  get: () => props.constitutionFilter,
  set: (v) => emit('update:constitutionFilter', v),
})

const sortModel = computed({
  get: () => props.sortBy,
  set: (v) => emit('update:sortBy', v),
})

const searchModel = computed({
  get: () => props.recipeSearchInput,
  set: (v) => emit('update:recipeSearchInput', v),
})

const effectPickerOptions = computed(() =>
  props.effectFilterOptions.map((o) => ({ text: o.label, value: o.value })),
)

const constitutionPickerOptions = computed(() =>
  props.constitutionFilterOptions.map((o) => ({ text: o.label, value: o.value })),
)

/** 与选项 value 对齐的展示名（顶栏 title 插槽用；勿写死 title 属性，否则 Vant 不会显示当前选中项） */
const effectSelectedLabel = computed(() => {
  const hit = props.effectFilterOptions.find((o) => o.value === props.effectFilter)
  return hit?.label ?? ''
})

const constitutionSelectedLabel = computed(() => {
  const hit = props.constitutionFilterOptions.find((o) => o.value === props.constitutionFilter)
  return hit?.label ?? ''
})
</script>

<template>
  <div class="toolbar page-card">
    <div class="toolbar__row">
      <div class="toolbar__controls">
        <!-- scoped 样式必须挂在「本组件模板内的元素」上；class 写在 van-dropdown-menu 根上不会带父级 data-v，导致 .toolbar__dd 选择器失效 -->
        <div class="toolbar__dd">
          <van-dropdown-menu :overlay="false">
            <van-dropdown-item v-model="effectModel" :options="effectPickerOptions">
              <template #title>
                <span class="dropdown-title-mix">
                  <span class="dropdown-title-mix__dim">功效</span>
                  <span class="dropdown-title-mix__sep" aria-hidden="true">·</span>
                  <span class="dropdown-title-mix__val">{{ effectSelectedLabel }}</span>
                </span>
              </template>
            </van-dropdown-item>
            <van-dropdown-item v-model="constitutionModel" :options="constitutionPickerOptions">
              <template #title>
                <span class="dropdown-title-mix">
                  <span class="dropdown-title-mix__dim">体质</span>
                  <span class="dropdown-title-mix__sep" aria-hidden="true">·</span>
                  <span class="dropdown-title-mix__val">{{ constitutionSelectedLabel }}</span>
                </span>
              </template>
            </van-dropdown-item>
          </van-dropdown-menu>
        </div>
        <div class="toolbar__sort">
          <span class="toolbar__label">排序</span>
          <van-radio-group v-model="sortModel" direction="horizontal" class="toolbar__radios">
            <van-radio name="collect">按收藏量</van-radio>
            <van-radio name="season">按季节</van-radio>
          </van-radio-group>
        </div>
      </div>
      <van-field
        v-model="searchModel"
        class="toolbar__search-field"
        clearable
        left-icon="search"
        placeholder="药膳名称、功效或标签，回车搜索"
        autocomplete="off"
        aria-label="搜索药膳，回车提交"
        @clear="emit('searchClear')"
        @keydown.enter="emit('searchEnter', $event)"
      />
    </div>
  </div>

  <div
    v-if="recipeSearchFallbackToKeywordOnly"
    class="search-filter-fallback page-card"
    role="status"
  >
    <van-icon name="info-o" class="search-filter-fallback__icon" />
    <p class="search-filter-fallback__text">
      当前功效或体质筛选下没有更精确匹配，已为您展示与「{{
        recipeSearchKeyword.trim()
      }}」相关的全部命中结果。调整筛选可进一步收窄。
    </p>
  </div>
</template>

<style scoped>
.page-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.toolbar {
  padding: var(--space-md) var(--space-lg);
  margin-bottom: var(--space-lg);
}

.toolbar__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md) var(--space-xl);
}

.toolbar__controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-md) var(--space-lg);
  flex: 1 1 auto;
  min-width: 0;
}

.toolbar__dd {
  /* 避免两项各占 50% 把筛选条拉得过宽（大屏下「功效 / 体质」框体过大） */
  flex: 0 1 auto;
  width: fit-content;
  max-width: 100%;
  min-width: 0;
  /**
   * 顶栏功效/体质：略高于全局 vant-theme 的 44px，仅本筛选区。
   * 点开后的选项列表仍用下方 --van-cell-*。
   */
  --van-dropdown-menu-height: 48px;
  --van-dropdown-menu-title-font-size: var(--font-size-lg);
  --van-dropdown-menu-title-line-height: var(--line-height-tight);
  --van-dropdown-menu-title-padding: 0 var(--space-sm);
  /**
   * 仅缩小「点开后的选项列表」：DropdownItem 内选项用 Cell 渲染，继承下列 token。
   */
  --van-cell-font-size: var(--font-size-sm);
  --van-cell-line-height: 1.25rem;
  --van-cell-vertical-padding: 6px;
  --van-cell-horizontal-padding: var(--space-md);
  --van-cell-icon-size: 15px;
  --van-dropdown-menu-content-max-height: min(70vh, 320px);
  /* 功效/体质下拉白底列表宽度（默认 van-popup--top 为 100% 屏宽） */
  --recommend-filter-dropdown-panel-width: min(252px, 86vw);
}

.toolbar__dd :deep(.van-dropdown-menu__bar) {
  width: fit-content;
  max-width: 100%;
  height: var(--van-dropdown-menu-height);
  box-shadow: none;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg-elevated);
  transition:
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.toolbar__dd :deep(.van-dropdown-menu__bar--opened) {
  border-color: color-mix(in srgb, var(--color-primary) 40%, var(--color-border));
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--color-primary) 22%, transparent);
}

.toolbar__dd :deep(.van-dropdown-menu__title) {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-primary);
}

.dropdown-title-mix {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  min-width: 0;
  gap: 0.15em;
  white-space: nowrap;
  /* 与 --van-dropdown-menu-title-font-size 一致；「功效」与选中项同字号 */
  font-size: var(--font-size-lg);
  line-height: var(--line-height-tight);
  font-family: SimSun, "Songti SC", STSong, "NSimSun", "Noto Serif CJK SC", serif;
}

.dropdown-title-mix__dim {
  flex-shrink: 0;
  color: var(--color-text-muted);
  font-weight: 500;
  font-size: inherit;
  font-family: inherit;
}

.dropdown-title-mix__sep {
  flex-shrink: 0;
  color: var(--color-text-muted);
  font-weight: 400;
  font-size: inherit;
  font-family: inherit;
}

.dropdown-title-mix__val {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 600;
  font-size: inherit;
  font-family: inherit;
  color: #2e7d32;
}

.toolbar__dd :deep(.van-dropdown-menu__title--active) {
  color: var(--color-primary);
  font-weight: 600;
}

.toolbar__dd :deep(.van-dropdown-menu__item) {
  flex: 0 0 auto;
  min-width: 0;
  max-width: min(11.5rem, 46vw);
}

/* 选项面板：收窄宽度并水平居中（不用 transform，避免与 popup 下滑 transition 冲突） */
.toolbar__dd :deep(.van-dropdown-item__content) {
  width: var(--recommend-filter-dropdown-panel-width);
  max-width: 100%;
  left: 0;
  right: 0;
  margin-left: auto;
  margin-right: auto;
}

/* 选项行：在 token 基础上略收紧（与顶栏 van-dropdown-menu__title 无重叠） */
.toolbar__dd :deep(.van-dropdown-item__option.van-cell) {
  min-height: 0;
}

.toolbar__dd :deep(.van-dropdown-item__option .van-cell__title) {
  font-size: var(--font-size-sm);
  line-height: var(--line-height-tight);
}

.toolbar__search-field {
  flex: 1 1 220px;
  max-width: min(100%, 420px);
  min-width: min(100%, 200px);
  padding: 0;
  margin: 0;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
}

.toolbar__search-field :deep(.van-field__body) {
  min-height: calc(var(--control-height) - 2px);
  padding: 8px 12px;
  align-items: center;
}

.toolbar__search-field :deep(.van-field__left-icon) {
  color: var(--color-primary);
  margin-right: 6px;
}

.toolbar__search-field :deep(.van-field__control) {
  font-size: var(--font-size-md);
  line-height: var(--line-height-tight);
  color: var(--color-text-primary);
}

.toolbar__search-field :deep(.van-field__clear) {
  color: var(--color-text-muted);
}

.toolbar__sort {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.toolbar__label {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  white-space: nowrap;
}

.toolbar__radios {
  gap: var(--space-sm);
}

.toolbar__radios :deep(.van-radio) {
  margin-right: 0;
  align-items: center;
}

.toolbar__radios :deep(.van-radio__label) {
  font-size: var(--font-size-md);
  color: var(--color-text-primary);
  line-height: var(--line-height-tight);
}

.toolbar__radios :deep(.van-radio__icon) {
  font-size: 18px;
}

.toolbar__radios :deep(.van-radio__icon--checked .van-icon) {
  color: var(--color-primary);
}

.search-filter-fallback {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
  padding: var(--space-md) var(--space-lg);
  margin-bottom: var(--space-lg);
  border: 1px solid color-mix(in srgb, var(--color-primary) 22%, var(--color-border));
  background: color-mix(in srgb, var(--color-primary) 6%, var(--color-bg-surface));
}

.search-filter-fallback__icon {
  flex-shrink: 0;
  margin-top: 2px;
  font-size: 18px;
  color: var(--color-primary);
}

.search-filter-fallback__text {
  margin: 0;
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-sm);
  line-height: 1.55;
  color: var(--color-text-secondary);
}

@media (max-width: 640px) {
  .toolbar__row {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar__search-field {
    max-width: none;
    min-width: 0;
  }
}
</style>

/** 配置 Tab 触发/批量触发后通知执行记录 Tab 刷新（keep-alive 双 Tab） */
export const TASK_EXECUTION_CHANGED = 'task-schedule:execution-changed';

export function notifyTaskExecutionChanged() {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(new CustomEvent(TASK_EXECUTION_CHANGED));
}

export function onTaskExecutionChanged(handler: () => void) {
  if (typeof window === 'undefined') {
    return () => undefined;
  }
  window.addEventListener(TASK_EXECUTION_CHANGED, handler);
  return () => window.removeEventListener(TASK_EXECUTION_CHANGED, handler);
}

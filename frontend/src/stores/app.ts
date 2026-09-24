import { defineStore } from 'pinia';

export const useAppStore = defineStore('app', {
  state: () => ({
    backendStatus: 'NOT_CHECKED',
  }),
  actions: {
    setBackendStatus(status: string) {
      this.backendStatus = status;
    },
  },
});

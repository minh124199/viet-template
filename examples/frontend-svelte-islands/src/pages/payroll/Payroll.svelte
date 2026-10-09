<script lang="ts">
  interface Props {
    period?: string;
    status?: string;
  }

  let { period = '2026-Q1', status = 'PROCESSED' }: Props = $props();
</script>

<div class="payroll-summary">
  <h3>Payroll Summary ({period})</h3>
  <span class="status-tag">{status}</span>
</div>

<style>
  .payroll-summary {
    padding: 1rem;
    border: 1px solid #10b981;
    border-radius: 6px;
    background: #ecfdf5;
  }
  .status-tag {
    font-weight: 600;
    color: #047857;
  }
</style>

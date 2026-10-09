<script lang="ts">
  export let period = '2026-Q1';
  export let status = 'PROCESSED';
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

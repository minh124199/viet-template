<script lang="ts">
  import type { Employee } from './types';
  import { formatCurrency, formatDate } from '../../shared/format';

  export let departmentName: string = 'Engineering';
  export let employees: Employee[] = [];

  let filter = '';

  $: filteredEmployees = employees.filter((emp) =>
    emp.name.toLowerCase().includes(filter.toLowerCase()) ||
    emp.role.toLowerCase().includes(filter.toLowerCase())
  );
</script>

<div class="employees-island">
  <header class="island-header">
    <h2>{departmentName} Department ({employees.length} members)</h2>
    <input
      type="search"
      bind:value={filter}
      placeholder="Filter by name or role..."
      class="filter-input"
    />
  </header>

  <table class="employee-table">
    <thead>
      <tr>
        <th>Name</th>
        <th>Role</th>
        <th>Department</th>
        <th>Salary</th>
        <th>Start Date</th>
      </tr>
    </thead>
    <tbody>
      {#each filteredEmployees as emp (emp.id)}
        <tr>
          <td><strong>{emp.name}</strong></td>
          <td><span class="employee-badge">{emp.role}</span></td>
          <td>{emp.department}</td>
          <td>{formatCurrency(emp.salary)}</td>
          <td>{formatDate(emp.startDate)}</td>
        </tr>
      {:else}
        <tr>
          <td colspan="5" class="empty-message">No matching employees found.</td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>

<style>
  .employees-island {
    padding: 1.5rem;
    background: #ffffff;
    border-radius: 8px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  }
  .island-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1rem;
    flex-wrap: wrap;
    gap: 0.5rem;
  }
  .island-header h2 {
    margin: 0;
    font-size: 1.25rem;
  }
  .filter-input {
    padding: 0.5rem 0.75rem;
    border: 1px solid #cbd5e1;
    border-radius: 6px;
    font-size: 0.875rem;
  }
  .empty-message {
    text-align: center;
    color: #64748b;
    padding: 2rem;
  }
</style>

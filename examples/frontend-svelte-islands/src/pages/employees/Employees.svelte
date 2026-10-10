<script lang="ts">
  import type { Employee } from './types';
  import { formatCurrency, formatDate } from '../../shared/format';
  import { readCsrfMetadata } from '../../shared/csrf';

  interface Props {
    departmentName?: string;
    employees?: Employee[];
  }

  let { departmentName = 'Engineering', employees = [] }: Props = $props();

  let filter = $state('');
  let followerCounts = $state<Record<string, number>>({});

  function getFollowers(emp: Employee): number {
    return followerCounts[emp.id] ?? emp.followers ?? 0;
  }

  let filteredEmployees = $derived(
    employees.filter((emp) =>
      emp.name.toLowerCase().includes(filter.toLowerCase()) ||
      emp.role.toLowerCase().includes(filter.toLowerCase())
    )
  );

  async function handleFollow(emp: Employee) {
    try {
      const csrf = readCsrfMetadata();
      const headers: Record<string, string> = { 'Accept': 'application/json' };
      if (csrf && csrf.token) {
        headers[csrf.headerName] = csrf.token;
      }

      const isSecure = typeof window !== 'undefined' && window.location.pathname.startsWith('/secure');
      const endpoint = isSecure
        ? `/secure/api/employees/${emp.id}/follow`
        : `/api/employees/${emp.id}/follow`;

      const response = await fetch(endpoint, {
        method: 'POST',
        headers,
      });
      if (response.ok) {
        const result = (await response.json()) as { id: string; followers: number };
        followerCounts[emp.id] = result.followers;
      }
    } catch (e) {
      console.error('Failed to follow employee', e);
    }
  }
</script>

<div class="employees-island" data-employee-island data-mounted="true" data-testid="svelte-island">
  <header class="island-header">
    <h2 data-testid="island-heading">{departmentName} Department ({employees.length} members)</h2>
    <input
      type="search"
      bind:value={filter}
      placeholder="Filter by name or role..."
      class="filter-input"
      data-testid="filter-input"
    />
  </header>

  <table class="employee-table">
    <thead>
      <tr>
        <th>Name</th>
        <th>Role</th>
        <th>Department</th>
        <th>Followers</th>
        <th>Salary</th>
        <th>Start Date</th>
        <th>Action</th>
      </tr>
    </thead>
    <tbody>
      {#each filteredEmployees as emp (emp.id)}
        <tr data-testid={`employee-row-${emp.id}`}>
          <td>
            <strong>{emp.name}</strong>
            {#if emp.bio}
              <div class="employee-bio" data-testid={`employee-bio-${emp.id}`}>{emp.bio}</div>
            {/if}
          </td>
          <td><span class="employee-badge">{emp.role}</span></td>
          <td>{emp.department}</td>
          <td>
            <span class="follower-count" data-testid={`follower-count-${emp.id}`}>
              Followers: <span data-testid={`followers-val-${emp.id}`}>{getFollowers(emp)}</span>
            </span>
          </td>
          <td>{formatCurrency(emp.salary)}</td>
          <td>{formatDate(emp.startDate)}</td>
          <td>
            <button
              class="follow-btn"
              data-testid={`follow-btn-${emp.id}`}
              onclick={() => handleFollow(emp)}
            >
              Follow
            </button>
          </td>
        </tr>
      {:else}
        <tr>
          <td colspan="7" class="empty-message">No matching employees found.</td>
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
  .employee-bio {
    font-size: 0.8rem;
    color: #64748b;
    margin-top: 0.25rem;
  }
  .follower-count {
    font-weight: 500;
  }
  .follow-btn {
    padding: 0.35rem 0.75rem;
    background-color: #2563eb;
    color: #ffffff;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    font-size: 0.875rem;
    font-weight: 500;
    transition: background-color 0.15s ease-in-out;
  }
  .follow-btn:hover {
    background-color: #1d4ed8;
  }
  .empty-message {
    text-align: center;
    color: #64748b;
    padding: 2rem;
  }
</style>

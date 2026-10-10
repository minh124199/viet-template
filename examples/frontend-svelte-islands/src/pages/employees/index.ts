import './employees.css';
import { mount } from 'svelte';
import Employees from './Employees.svelte';
import type { EmployeePageData } from './types';
import { readClientData } from '../../client-data';

const target = document.getElementById('employees-island');

if (target) {
  // Clear SSR fallback placeholder before mounting interactive island
  target.innerHTML = '';
  target.setAttribute('data-mounted', 'true');

  const pageData = readClientData<EmployeePageData>('employees-data');
  mount(Employees, {
    target,
    props: {
      departmentName: pageData.departmentName,
      employees: pageData.employees,
    },
  });
}

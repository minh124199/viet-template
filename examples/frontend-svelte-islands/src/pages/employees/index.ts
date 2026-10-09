import './employees.css';
import Employees from './Employees.svelte';
import type { EmployeePageData } from './types';
import { readClientData } from '../../client-data';

const target = document.getElementById('employees-island');

if (target) {
  // Clear SSR fallback placeholder before mounting interactive island
  target.innerHTML = '';

  const pageData = readClientData<EmployeePageData>('employees-data');
  new Employees({
    target,
    props: {
      departmentName: pageData.departmentName,
      employees: pageData.employees,
    },
  });
}

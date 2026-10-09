export interface Employee {
  id: string;
  name: string;
  role: string;
  department: string;
  salary: number;
  startDate: string;
}

export interface EmployeePageData {
  departmentName: string;
  employees: Employee[];
}

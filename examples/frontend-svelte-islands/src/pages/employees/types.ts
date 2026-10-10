export interface Employee {
  id: string;
  name: string;
  role: string;
  department: string;
  salary: number;
  startDate: string;
  followers?: number;
  bio?: string;
}

export interface EmployeePageData {
  departmentName: string;
  employees: Employee[];
}

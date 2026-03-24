import type { Config } from 'jest';

const config: Config = {
  preset: 'jest-preset-angular',
  setupFilesAfterEnv: ['<rootDir>/setup-jest.ts'],
  moduleNameMapper: {
    '^@domain/(.*)$': '<rootDir>/src/app/domain/$1',
    '^@application/(.*)$': '<rootDir>/src/app/application/$1',
    '^@infrastructure/(.*)$': '<rootDir>/src/app/infrastructure/$1',
    '^@presentation/(.*)$': '<rootDir>/src/app/presentation/$1',
  },
  testPathIgnorePatterns: ['/node_modules/', '/dist/', '/e2e/'],
};

export default config;

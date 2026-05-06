import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app';
import { config } from './app/app.config.server';

/**
 * In Angular 21, the bootstrap function exported from main.server.ts
 * is called with a BootstrapContext. This context must be passed as 
 * the third argument to bootstrapApplication.
 */
const bootstrap = (context: any) => bootstrapApplication(AppComponent, config, context);

export default bootstrap;

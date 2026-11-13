# Main Objective

The primary goal is to consolidate all reservations within the platform and provide an API to access them.

## Features and Requirements

- Guest Records (Spanish law)
    * Integration with checkin.com for the sas.hospedajes registry
    * Note: When importing reservations via iCal, guest details such as name, number of guests, and number of children
      are typically missing.
      The system must store and manage complete guest details.

- Bookings
  * A booking can be imported by the ical plugins
  * A booking can be manually created
  * Future: Users can book properties via /property/{id}/book

- Smart Lock Integration
    * Automatically generate property access keys.
    * Integrate with Nuki and Unifi systems for key management.

- Cleaning and Maintenance Tracking
    * Enable registration of cleaning and maintenance activities.
    * Cleanings can be linked to a booking or exist as standalone calendar events.
    * Typically, a cleaning is associated with a booking.
    * Allow adding photos or notes to each record.
    * Provide a mobile-friendly web app for staff to use on the go.

House Guide

* Make the house guide accessible from mobile devices for guests.

## DB Notes.

- Al registrarse un usuario se le crea automaticamente una account.
- Un usuario solo puede pertenecer a una account
- Cuando se crear una propiedad, se anade al account del usuario.
- Una propiedad solo puede pertenecer an account
- A un usuario se le puede invitar a una propiedad en concreto (pero esto lo dejamos para la siguiente fase)

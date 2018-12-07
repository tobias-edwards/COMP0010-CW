package com.trafficmon;

import java.math.BigDecimal;
import java.util.*;
import static java.time.temporal.ChronoUnit.MINUTES;

public class CongestionChargeSystem {

    public static final BigDecimal CHARGE_RATE_POUNDS_PER_MINUTE = new BigDecimal(0.05);

    private final List<ZoneBoundaryCrossing> eventLog = new ArrayList<ZoneBoundaryCrossing>();
    //is a list of vehicles and their timestamps (entry/exit)

    public void vehicleEnteringZone(Vehicle vehicle) {
        //adds the vehicle object and its entry time to list
        eventLog.add(ZoneBoundaryCrossingFactory.getZoneCrossing("Entry", vehicle, new SystemClock()));
    }
    public void vehicleEnteringZone(Vehicle vehicle, Clock clock){
        //adds the vehicle object and its entry time to list
        eventLog.add(ZoneBoundaryCrossingFactory.getZoneCrossing("Entry", vehicle, clock));
    }


    public void vehicleLeavingZone(Vehicle vehicle) {
        if (!previouslyRegistered(vehicle)) {
            //unregistered vehicles are ignored
            return;
        }
        //adds vehicle and its exit time to list
        eventLog.add(ZoneBoundaryCrossingFactory.getZoneCrossing("Exit", vehicle, new SystemClock()));
    }

    public void vehicleLeavingZone(Vehicle vehicle, Clock clock) {
        if (!previouslyRegistered(vehicle)) {
            //unregistered vehicles are ignored
            return;
        }
        //adds vehicle and its exit time to list
        eventLog.add(ZoneBoundaryCrossingFactory.getZoneCrossing("Exit", vehicle, clock));
    }

    public List getEventlog(){
        return eventLog;
    }

    public void calculateCharges() {

        Map<Vehicle, List<ZoneBoundaryCrossing>> crossingsByVehicle = new HashMap<Vehicle, List<ZoneBoundaryCrossing>>();

        for (ZoneBoundaryCrossing crossing : eventLog) {
            Vehicle vehicle = crossing.getVehicle();

            //Finds vehicle in list
            if (!crossingsByVehicle.containsKey(vehicle)) {
                //stores each vehicle that has an activity once.
                crossingsByVehicle.put(vehicle, new ArrayList<ZoneBoundaryCrossing>());
            }
            crossingsByVehicle.get(vehicle).add(crossing);
        }

        for (Map.Entry<Vehicle, List<ZoneBoundaryCrossing>> vehicleCrossings : crossingsByVehicle.entrySet()) {
            //iterates through every vehicle stored in crossingsByVehicle
            Vehicle vehicle = vehicleCrossings.getKey();
            //Stores all activities of 'vehicle in crossings'
            List<ZoneBoundaryCrossing> crossings = vehicleCrossings.getValue();

            if (!checkOrderingOf(crossings)) {
                OperationsTeam.getInstance().triggerInvestigationInto(vehicle);
            } else {
                //calls calculation function
                BigDecimal charge = calculateChargeForTimeInZone(crossings);

                try {
                    RegisteredCustomerAccountsService.getInstance().accountFor(vehicle).deduct(charge);
                } catch (InsufficientCreditException | AccountNotRegisteredException e){
                    OperationsTeam.getInstance().issuePenaltyNotice(vehicle, charge); }
            }
        }
    }

    private BigDecimal calculateChargeForTimeInZone(List<ZoneBoundaryCrossing> crossings) {
        //calculates charge for single vehicle for all of its activities

        BigDecimal charge = new BigDecimal(0);

        //lastEvent starts as an entryEvent
        //crossings should be an exitEvent else theres a bug.
        //after an iteration, lastEvent becomes the exitEvent and crossings becomes the next EntryEvent
        //if the vehicle re-enters. else, the loop ends.

        ZoneBoundaryCrossing lastEvent = crossings.get(0);

        for (ZoneBoundaryCrossing crossing : crossings.subList(1, crossings.size())) {

            if (crossing instanceof ExitEvent) {
                //lastEvent = entryevent
                if (lastEvent.timestamp().getHour() < 14){

                }
                charge = charge.add(
                        new BigDecimal(MINUTES.between(lastEvent.timestamp(), crossing.timestamp()))
                                .multiply(CHARGE_RATE_POUNDS_PER_MINUTE));
            }

            lastEvent = crossing;
        }

        return charge;
    }

    private boolean previouslyRegistered(Vehicle vehicle) {
        for (ZoneBoundaryCrossing crossing : eventLog)
            if (crossing.getVehicle().equals(vehicle)) {
                return true;
            }

        return false;
    }

    private boolean checkOrderingOf(List<ZoneBoundaryCrossing> crossings) {
        //method finds the last occurrings event.
        ZoneBoundaryCrossing lastEvent = crossings.get(0);

        for (ZoneBoundaryCrossing crossing : crossings.subList(1, crossings.size())) {
            //the last event should have a greater timestamp || both time stamps shouldn't be entry events || both time stamps shouldn't be exit events
            if ((crossing.timestamp().compareTo(lastEvent.timestamp()) < 0) ||
               (crossing instanceof EntryEvent && lastEvent instanceof EntryEvent) ||
               (crossing instanceof ExitEvent && lastEvent instanceof ExitEvent)) {
                return false;
            }
            lastEvent = crossing;
        }

        return true;
    }

}

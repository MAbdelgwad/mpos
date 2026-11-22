import api from '../../config/apiConfig';
import {baseUnitsActionType,toastType} from '../../constants';
import {addToast} from './toastAction';
// Retain the shared lookup Redux slot to avoid resetting other catalog forms.
export const fetchUnitChoices = () => async dispatch => {
    try {
        const response=await api.get('units?page[size]=0');
        dispatch({type:baseUnitsActionType.FETCH_ALL_BASE_UNITS,payload:response.data.data});
    } catch(error) {
        dispatch(addToast({text:error.response?.data?.message || error.message,type:toastType.ERROR}));
    }
};
